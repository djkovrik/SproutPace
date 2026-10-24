package com.sedsoftware.sproutpace

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.sedsoftware.sproutpace.compose.App
import com.sedsoftware.sproutpace.compose.AndroidProductionAppGraph
import com.sedsoftware.sproutpace.compose.PlatformActionHandler
import com.sedsoftware.sproutpace.compose.PlatformActionFailure
import com.sedsoftware.sproutpace.compose.PlatformActionKind
import com.sedsoftware.sproutpace.compose.PlatformActionRequest
import com.sedsoftware.sproutpace.component.pendingPhotoDraftValue
import com.sedsoftware.sproutpace.component.pendingPhotoToken
import com.sedsoftware.sproutpace.platform.AndroidSystemPickerHost
import com.sedsoftware.sproutpace.platform.PickedContentKind
import kotlinx.coroutines.launch

class AppActivity : ComponentActivity() {
    private var pendingDestinationResult: ((String) -> Unit)? = null
    private var pendingDestinationFailure: ((PlatformActionFailure) -> Unit)? = null
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
                request = request,
                onResult = onResult,
            )
            PlatformActionKind.CREATE_DOCUMENT -> createDocumentDestination(request, onResult)
            PlatformActionKind.CHOOSE_PHOTO -> importPickedContent(
                kind = PickedContentKind.PHOTO,
                request = request,
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
        bindRootBackNavigation(appGraph.root)
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

    private fun importPickedContent(
        kind: PickedContentKind,
        request: PlatformActionRequest,
        onResult: (String) -> Unit,
    ) {
        lifecycleScope.launch {
            val failure = if (kind == PickedContentKind.PHOTO) PlatformActionFailure.PHOTO_IMPORT
                else PlatformActionFailure.DOCUMENT_IMPORT
            systemPickerHost.pickAndImport(kind)
                .onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    request.onFailure?.invoke(failure)
                }
                .getOrNull()
                ?.let { imported ->
                    val value = when (kind) {
                        PickedContentKind.PHOTO -> imported.pendingToken?.let(::pendingPhotoDraftValue)
                        PickedContentKind.BACKUP_ARCHIVE -> imported.appOwnedPath?.let { path ->
                            com.sedsoftware.sproutpace.data.SelectedBackupDocument(path, imported.displayName).encode()
                        }
                    }
                    if (value.isNullOrBlank()) request.onFailure?.invoke(failure)
                    value
                }
                ?.takeIf(String::isNotBlank)
                ?.let(onResult)
        }
    }

    private fun createDocumentDestination(
        request: PlatformActionRequest,
        onResult: (String) -> Unit,
    ) {
        val suggestedName = request.suggestedName?.takeIf(String::isNotBlank)
        if (suggestedName == null || pendingDestinationResult != null) {
            request.onFailure?.invoke(PlatformActionFailure.DOCUMENT_DESTINATION)
            return
        }
        val launcher = when (request.mediaType) {
            BACKUP_MEDIA_TYPE -> backupDestinationLauncher
            CSV_MEDIA_TYPE -> csvDestinationLauncher
            else -> {
                request.onFailure?.invoke(PlatformActionFailure.DOCUMENT_DESTINATION)
                return
            }
        }
        pendingDestinationResult = onResult
        pendingDestinationFailure = request.onFailure
        runCatching { launcher.launch(suggestedName) }
            .onFailure {
                pendingDestinationResult = null
                pendingDestinationFailure = null
                request.onFailure?.invoke(PlatformActionFailure.DOCUMENT_DESTINATION)
            }
    }

    private fun completeDestination(uri: Uri?) {
        val result = pendingDestinationResult
        val failure = pendingDestinationFailure
        pendingDestinationResult = null
        pendingDestinationFailure = null
        if (uri == null) return
        if (uri.scheme == "content") result?.invoke(uri.toString())
        else failure?.invoke(PlatformActionFailure.DOCUMENT_DESTINATION)
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
    val background = MaterialTheme.colorScheme.background.toArgb()
    SideEffect {
        val window = (view.context as Activity).window
        window.setBackgroundDrawable(ColorDrawable(background))
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
    }
}

private const val BACKUP_MEDIA_TYPE = "application/zip"
private const val CSV_MEDIA_TYPE = "text/csv"
