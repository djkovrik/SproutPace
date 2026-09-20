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

private const val ACTION_DELIVER_REMINDER = "com.sedsoftware.sproutpace.action.DELIVER_REMINDER"
private const val EXTRA_ID = "physical_reminder_id"
private const val EXTRA_TITLE_KEY = "title_key"
private const val EXTRA_BODY_KEY = "body_key"
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
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(context.getString(payload.title.resourceId()))
            .setContentText(context.getString(payload.body.resourceId()))
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(PhysicalReminderId(payload.id).requestCode(), notification)
        }
        context.getSharedPreferences("physical_reminders.v1", Context.MODE_PRIVATE).let { preferences ->
            val ids = preferences.getStringSet("ids", emptySet()).orEmpty().toMutableSet().apply { remove(payload.id) }
            preferences.edit().putStringSet("ids", ids).apply()
        }
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
        AndroidReconciliationSignalStore(context).record(reason)
    }
}

class AndroidReconciliationSignalStore(context: Context) {
    private val preferences = context.getSharedPreferences("reminder_reconciliation.v1", Context.MODE_PRIVATE)

    fun record(reason: ReconciliationReason) {
        val reasons = preferences.getStringSet("reasons", emptySet()).orEmpty().toMutableSet()
        reasons += reason.name
        check(preferences.edit().putStringSet("reasons", reasons).commit())
    }

    fun consume(): Set<ReconciliationReason> {
        val reasons = preferences.getStringSet("reasons", emptySet()).orEmpty()
            .mapNotNullTo(linkedSetOf()) { runCatching { ReconciliationReason.valueOf(it) }.getOrNull() }
        preferences.edit().remove("reasons").apply()
        return reasons
    }
}

private fun PhysicalReminder.pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
    context,
    id.requestCode(),
    Intent(context, ReminderDeliveryReceiver::class.java)
        .setAction(ACTION_DELIVER_REMINDER)
        .putExtra(EXTRA_ID, id.value)
        .putExtra(EXTRA_TITLE_KEY, titleKey.name)
        .putExtra(EXTRA_BODY_KEY, bodyKey.name),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
)

private fun Intent.nativeText(extra: String): NativeTextKey? =
    getStringExtra(extra)?.let { runCatching { NativeTextKey.valueOf(it) }.getOrNull() }

private data class ReminderPayload(val id: String, val title: NativeTextKey, val body: NativeTextKey)

private fun Intent.reminderPayload(): ReminderPayload? {
    val id = getStringExtra(EXTRA_ID)
    val title = nativeText(EXTRA_TITLE_KEY)
    val body = nativeText(EXTRA_BODY_KEY)
    return if (id != null && title != null && body != null) ReminderPayload(id, title, body) else null
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
