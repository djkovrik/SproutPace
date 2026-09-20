package com.sedsoftware.sproutpace

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.sedsoftware.sproutpace.compose.App
import com.sedsoftware.sproutpace.compose.AndroidProductionAppGraph
import com.sedsoftware.sproutpace.compose.PlatformActionHandler
import com.sedsoftware.sproutpace.compose.PlatformActionKind
import com.sedsoftware.sproutpace.compose.PlatformActionRequest
import com.sedsoftware.sproutpace.component.pendingPhotoDraftValue
import com.sedsoftware.sproutpace.component.pendingPhotoToken
import com.sedsoftware.sproutpace.platform.AndroidSystemPickerHost
import com.sedsoftware.sproutpace.platform.PickedContentKind
import kotlinx.coroutines.launch

class AppActivity : ComponentActivity() {
    private var pendingDestinationResult: ((String) -> Unit)? = null
    val systemPickerHost: AndroidSystemPickerHost by lazy(LazyThreadSafetyMode.NONE) {
        AndroidSystemPickerHost(this)
    }
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        appGraph.notificationPermissionService.onPermissionResult(granted)
    }
    private val backupDestinationLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MEDIA_TYPE),
    ) { uri -> completeDestination(uri) }
    private val csvDestinationLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument(CSV_MEDIA_TYPE),
    ) { uri -> completeDestination(uri) }
    private val appGraph: AndroidProductionAppGraph
        get() = (application as SproutPaceApplication).graph
    private val platformActionHandler = PlatformActionHandler { request, onResult ->
        when (request.kind) {
            PlatformActionKind.CHOOSE_DOCUMENT -> importPickedContent(
                kind = PickedContentKind.BACKUP_ARCHIVE,
                onResult = onResult,
            )
            PlatformActionKind.CREATE_DOCUMENT -> createDocumentDestination(request, onResult)
            PlatformActionKind.CHOOSE_PHOTO -> importPickedContent(
                kind = PickedContentKind.PHOTO,
                onResult = onResult,
            )
            PlatformActionKind.DISCARD_PENDING_PHOTO -> lifecycleScope.launch {
                request.opaqueValue?.let(::pendingPhotoToken)?.let { systemPickerHost.discardPendingPhoto(it) }
            }
            PlatformActionKind.REQUEST_NOTIFICATION_PERMISSION -> lifecycleScope.launch {
                appGraph.requestNotificationPermission()
            }
            PlatformActionKind.OPEN_SYSTEM_SETTINGS -> openSystemSettings()
            PlatformActionKind.OPEN_PRIVACY_POLICY -> openConfiguredPrivacyPolicy()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appGraph.notificationPermissionService.attach(this, notificationPermissionLauncher)
        systemPickerHost
        enableEdgeToEdge()
        setContent {
            App(
                root = appGraph.root,
                platformActions = platformActionHandler,
                onThemeChanged = { ThemeChanged(it) },
            )
        }
    }

    override fun onStart() {
        super.onStart()
        appGraph.onStart()
    }

    override fun onResume() {
        super.onResume()
        appGraph.onResume()
    }

    override fun onPause() {
        appGraph.onPause()
        super.onPause()
    }

    override fun onStop() {
        appGraph.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        appGraph.notificationPermissionService.detach(this)
        super.onDestroy()
    }

    private fun importPickedContent(kind: PickedContentKind, onResult: (String) -> Unit) {
        lifecycleScope.launch {
            systemPickerHost.pickAndImport(kind)
                .getOrNull()
                ?.let { imported ->
                    when (kind) {
                        PickedContentKind.PHOTO -> imported.pendingToken?.let(::pendingPhotoDraftValue)
                        PickedContentKind.BACKUP_ARCHIVE -> imported.appOwnedPath
                    }
                }
                ?.takeIf(String::isNotBlank)
                ?.let(onResult)
        }
    }

    private fun createDocumentDestination(
        request: PlatformActionRequest,
        onResult: (String) -> Unit,
    ) {
        val suggestedName = request.suggestedName?.takeIf(String::isNotBlank) ?: return
        if (pendingDestinationResult != null) return
        val launcher = when (request.mediaType) {
            BACKUP_MEDIA_TYPE -> backupDestinationLauncher
            CSV_MEDIA_TYPE -> csvDestinationLauncher
            else -> return
        }
        pendingDestinationResult = onResult
        runCatching { launcher.launch(suggestedName) }
            .onFailure { pendingDestinationResult = null }
    }

    private fun completeDestination(uri: Uri?) {
        val result = pendingDestinationResult
        pendingDestinationResult = null
        uri?.takeIf { it.scheme == "content" }?.toString()?.let { value -> result?.invoke(value) }
    }

    private fun openSystemSettings() {
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null),
                ),
            )
        }
    }

    private fun openConfiguredPrivacyPolicy() {
        val uri = BuildConfig.PRIVACY_POLICY_URL
            .takeIf(String::isNotBlank)
            ?.let(Uri::parse)
            ?.takeIf { candidate ->
                candidate.scheme.equals("https", ignoreCase = true) && !candidate.host.isNullOrBlank()
            }
            ?: return
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }
}

@Composable
private fun ThemeChanged(isDark: Boolean) {
    val view = LocalView.current
    LaunchedEffect(isDark) {
        val window = (view.context as Activity).window
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
    }
}

private const val BACKUP_MEDIA_TYPE = "application/zip"
private const val CSV_MEDIA_TYPE = "text/csv"
