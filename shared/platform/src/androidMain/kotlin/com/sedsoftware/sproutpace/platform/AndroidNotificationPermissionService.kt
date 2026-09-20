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
        mutableState.value = when {
            !NotificationManagerCompat.from(applicationContext).areNotificationsEnabled() ->
                PermissionState.Denied(canRequestAgain = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> PermissionState.Granted
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED -> PermissionState.Granted
            else -> PermissionState.Denied(
                canRequestAgain = !preferences.getBoolean("requested", false) ||
                    activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true,
            )
        }
        return state.value
    }

    override suspend fun request(): PermissionState {
        return when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> check()
            pendingRequest != null -> PermissionState.Requesting
            requestLauncher == null -> check()
            else -> {
                mutableState.value = PermissionState.Requesting
                suspendCancellableCoroutine { continuation ->
                    preferences.edit().putBoolean("requested", true).apply()
                    pendingRequest = { granted ->
                        pendingRequest = null
                        mutableState.value = if (granted) PermissionState.Granted else PermissionState.Denied(
                            canRequestAgain = activity?.shouldShowRequestPermissionRationale(
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) == true,
                        )
                        if (continuation.isActive) continuation.resume(state.value)
                    }
                    continuation.invokeOnCancellation { pendingRequest = null }
                    checkNotNull(requestLauncher).launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

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
