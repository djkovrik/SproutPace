package com.sedsoftware.sproutpace.platform

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sedsoftware.sproutpace.platform.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val ACTION_DELIVER_REMINDER = "com.sedsoftware.sproutpace.action.DELIVER_REMINDER"
private const val EXTRA_ID = "physical_reminder_id"
private const val EXTRA_TITLE_KEY = "title_key"
private const val EXTRA_BODY_KEY = "body_key"
private const val EXTRA_FIRE_AT = "fire_at_epoch_millis"
private const val EXTRA_KIND = "reminder_kind"
private const val RECEIVER_WORK_TIMEOUT_MILLIS = 8_000L
private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
private val deliveryMutex = Mutex()

interface AndroidReminderRuntimeOwner {
    suspend fun reconcileReminderEnvironment(reason: ReconciliationReason)
    suspend fun evaluateReminderDelivery(id: PhysicalReminderId, fireAtEpochMillis: Long): Boolean
}
private const val REMINDER_CHANNEL_ID = "reminders.v1"

class AndroidReminderDriver(private val context: Context) : ReminderDriver {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val preferences = context.getSharedPreferences("physical_reminders.v1", Context.MODE_PRIVATE)

    override suspend fun registeredIds(): Set<PhysicalReminderId> =
        preferences.getStringSet("ids", emptySet()).orEmpty().mapTo(linkedSetOf(), ::PhysicalReminderId)

    override suspend fun schedule(reminder: PhysicalReminder): Result<Unit> = runCatching {
        val operation = reminder.pendingIntent(context)
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.fireAtEpochMillis, operation)
        updateIds { it += reminder.id.value }
    }

    override suspend fun cancel(id: PhysicalReminderId): Result<Unit> = runCatching {
        val operation = PendingIntent.getBroadcast(
            context,
            id.requestCode(),
            Intent(context, ReminderDeliveryReceiver::class.java).setAction(ACTION_DELIVER_REMINDER),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        operation?.let(alarms::cancel)
        operation?.cancel()
        updateIds { it -= id.value }
    }

    private fun updateIds(change: (MutableSet<String>) -> Unit) {
        val values = preferences.getStringSet("ids", emptySet()).orEmpty().toMutableSet()
        change(values)
        check(preferences.edit().putStringSet("ids", values).commit())
    }
}

class ReminderDeliveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DELIVER_REMINDER) return
        val payload = intent.reminderPayload() ?: return
        val pending = goAsync()
        receiverScope.launch {
            runReminderBroadcast(
                work = {
                    withTimeout(RECEIVER_WORK_TIMEOUT_MILLIS) {
                        deliveryMutex.withLock { deliver(context.applicationContext, payload) }
                    }
                },
                onFailure = { AndroidReconciliationSignalStore(context).record(ReconciliationReason.REMINDER_DELIVERED) },
                finish = pending::finish,
            )
        }
    }

    private suspend fun deliver(context: Context, payload: ReminderPayload) {
        val runtime = context as? AndroidReminderRuntimeOwner ?: error("reminder_runtime_unavailable")
        val preferences = context.getSharedPreferences("physical_reminders.v1", Context.MODE_PRIVATE)
        // Reconciliation retires obsolete soak IDs and replaces the stable daily alarm atomically.
        // Removing IDs here could erase a registration made by a concurrent cold-start reconciliation.
        val token = if (payload.kind == ReminderKind.DAILY_SUMMARY) {
            SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
        } else payload.fireAt.toString()
        val claimKey = "delivered:${payload.id}"
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(context.getString(payload.title.resourceId()))
            .setContentText(context.getString(payload.body.resourceId()))
            .setAutoCancel(true)
            .build()
        deliverReminderOnce(
            token = token,
            evaluateAndRearm = { runtime.evaluateReminderDelivery(PhysicalReminderId(payload.id), payload.fireAt) },
            readClaim = { preferences.getString(claimKey, null) },
            writeClaim = { claim -> check(preferences.edit().putString(claimKey, claim).commit()) },
            post = { NotificationManagerCompat.from(context).notify(PhysicalReminderId(payload.id).requestCode(), notification) },
        )
    }
}

class ReminderEnvironmentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reason = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> ReconciliationReason.DEVICE_RESTART
            Intent.ACTION_MY_PACKAGE_REPLACED -> ReconciliationReason.APP_REPLACED
            Intent.ACTION_TIMEZONE_CHANGED -> ReconciliationReason.TIME_ZONE_CHANGED
            else -> return
        }
        val signals = AndroidReconciliationSignalStore(context)
        signals.record(reason)
        val pending = goAsync()
        receiverScope.launch {
            runReminderBroadcast(
                work = {
                    withTimeout(RECEIVER_WORK_TIMEOUT_MILLIS) {
                        val runtime = context.applicationContext as? AndroidReminderRuntimeOwner
                            ?: error("reminder_runtime_unavailable")
                        runtime.reconcileReminderEnvironment(reason)
                        signals.acknowledge(reason)
                    }
                },
                onFailure = { signals.record(reason) },
                finish = pending::finish,
            )
        }
    }
}

class AndroidReconciliationSignalStore(context: Context) {
    private val preferences = context.getSharedPreferences("reminder_reconciliation.v1", Context.MODE_PRIVATE)

    fun record(reason: ReconciliationReason) {
        val reasons = preferences.getStringSet("reasons", emptySet()).orEmpty().toMutableSet()
        reasons += reason.name
        check(preferences.edit().putStringSet("reasons", reasons).commit())
    }

    fun acknowledge(reason: ReconciliationReason) {
        val reasons = preferences.getStringSet("reasons", emptySet()).orEmpty().toMutableSet()
        reasons -= reason.name
        check(preferences.edit().putStringSet("reasons", reasons).commit())
    }

    fun pending(): Set<ReconciliationReason> {
        val reasons = preferences.getStringSet("reasons", emptySet()).orEmpty()
            .mapNotNullTo(linkedSetOf()) { runCatching { ReconciliationReason.valueOf(it) }.getOrNull() }
        return reasons
    }
}

private fun PhysicalReminder.pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
    context,
    id.requestCode(),
    Intent(context, ReminderDeliveryReceiver::class.java)
        .setAction(ACTION_DELIVER_REMINDER)
        .putExtra(EXTRA_ID, id.value)
        .putExtra(EXTRA_FIRE_AT, fireAtEpochMillis)
        .putExtra(EXTRA_KIND, kind.name)
        .putExtra(EXTRA_TITLE_KEY, titleKey.name)
        .putExtra(EXTRA_BODY_KEY, bodyKey.name),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
)

private fun Intent.nativeText(extra: String): NativeTextKey? =
    getStringExtra(extra)?.let { runCatching { NativeTextKey.valueOf(it) }.getOrNull() }

private data class ReminderPayload(
    val id: String,
    val title: NativeTextKey,
    val body: NativeTextKey,
    val kind: ReminderKind,
    val fireAt: Long,
)

private fun Intent.reminderPayload(): ReminderPayload? {
    val id = getStringExtra(EXTRA_ID)
    val title = nativeText(EXTRA_TITLE_KEY)
    val body = nativeText(EXTRA_BODY_KEY)
    val kind = getStringExtra(EXTRA_KIND)?.let { runCatching { ReminderKind.valueOf(it) }.getOrNull() }
    val fireAt = getLongExtra(EXTRA_FIRE_AT, -1)
    if (fireAt < 0) return null
    return if (id != null && title != null && body != null && kind != null) {
        ReminderPayload(id, title, body, kind, fireAt)
    } else null
}

private fun NativeTextKey.resourceId(): Int = when (this) {
    NativeTextKey.REMINDER_CHANNEL_NAME -> R.string.notification_channel_reminders
    NativeTextKey.DAILY_SUMMARY_TITLE -> R.string.notification_daily_summary_title
    NativeTextKey.DAILY_SUMMARY_BODY -> R.string.notification_daily_summary_body
    NativeTextKey.SOAK_COMPLETE_TITLE -> R.string.notification_soak_complete_title
    NativeTextKey.SOAK_COMPLETE_BODY -> R.string.notification_soak_complete_body
}

private fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
        NotificationChannel(
            REMINDER_CHANNEL_ID,
            context.getString(NativeTextKey.REMINDER_CHANNEL_NAME.resourceId()),
            NotificationManager.IMPORTANCE_DEFAULT,
        ),
    )
}
