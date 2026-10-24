package com.sedsoftware.sproutpace.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidNotificationPermissionService(
    context: Context,
) : NotificationPermissionService {
    private val applicationContext = context.applicationContext
    private val preferences = applicationContext.getSharedPreferences("notification_permission.v1", 0)
    private val mutableState = MutableStateFlow<PermissionState>(PermissionState.Unknown)
    override val state = mutableState.asStateFlow()
    private var activity: ComponentActivity? = null
    private var requestLauncher: ActivityResultLauncher<String>? = null

    private var pendingRequest: ((Boolean) -> Unit)? = null

    override suspend fun check(): PermissionState {
        mutableState.value = PermissionState.Checking
        mutableState.value = currentPermissionState()
        return state.value
    }

    override suspend fun request(): PermissionState {
        if (pendingRequest != null) return PermissionState.Requesting
        val current = check()
        return when {
            current != PermissionState.Denied(true) -> current
            requestLauncher == null -> current
            else -> {
                mutableState.value = PermissionState.Requesting
                suspendCancellableCoroutine { continuation ->
                    preferences.edit().putBoolean("requested", true).apply()
                    pendingRequest = { _ ->
                        pendingRequest = null
                        mutableState.value = currentPermissionState()
                        if (continuation.isActive) continuation.resume(state.value)
                    }
                    continuation.invokeOnCancellation { pendingRequest = null }
                    checkNotNull(requestLauncher).launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    private fun currentPermissionState(): PermissionState = androidNotificationPermissionState(
        runtimePermissionRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
        notificationsEnabled = NotificationManagerCompat.from(applicationContext).areNotificationsEnabled(),
        runtimePermissionGranted = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED,
        previouslyRequested = preferences.getBoolean("requested", false),
        shouldShowRationale = activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true,
    )

    fun attach(activity: ComponentActivity, requestLauncher: ActivityResultLauncher<String>) {
        this.activity = activity
        this.requestLauncher = requestLauncher
    }

    fun detach(activity: ComponentActivity) {
        if (this.activity === activity) {
            this.activity = null
            requestLauncher = null
            pendingRequest = null
        }
    }

    fun onPermissionResult(granted: Boolean) {
        pendingRequest?.invoke(granted)
    }
}

object AndroidPlatformServiceFactory {
    fun notificationPermission(
        activity: ComponentActivity,
        requestLauncher: ActivityResultLauncher<String>,
    ): AndroidNotificationPermissionService = AndroidNotificationPermissionService(activity).also {
        it.attach(activity, requestLauncher)
    }

    fun reminders(activity: ComponentActivity): AndroidReminderDriver = AndroidReminderDriver(activity)

    fun pickers(activity: ComponentActivity): AndroidSystemPickerHost = AndroidSystemPickerHost(activity)

    fun crashReporting(): AndroidCrashReportingDriver = AndroidCrashReportingDriver()
}
