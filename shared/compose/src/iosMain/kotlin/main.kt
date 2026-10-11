import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.ComposeUIViewController
import com.sedsoftware.sproutpace.compose.App
import com.sedsoftware.sproutpace.compose.IosProductionAppGraph
import com.sedsoftware.sproutpace.compose.PlatformActionHandler
import com.sedsoftware.sproutpace.compose.PlatformActionFailure
import com.sedsoftware.sproutpace.compose.PlatformActionKind
import com.sedsoftware.sproutpace.compose.PlatformActionRequest
import com.sedsoftware.sproutpace.component.pendingPhotoDraftValue
import com.sedsoftware.sproutpace.component.pendingPhotoToken
import platform.UIKit.UIApplication
import platform.UIKit.UIStatusBarStyleDarkContent
import platform.UIKit.UIStatusBarStyleLightContent
import platform.UIKit.UIViewController
import platform.UIKit.setStatusBarStyle

interface IosPlatformActionResult {
    fun accept(value: String)
    fun fail() = Unit
    fun acceptDocument(location: String, displayName: String) {
        accept(com.sedsoftware.sproutpace.data.SelectedBackupDocument(location, displayName).encode())
    }
}

interface IosPlatformActionBridge {
    fun chooseDocument(result: IosPlatformActionResult)
    fun createDocument(suggestedName: String, mediaType: String, result: IosPlatformActionResult)
    fun choosePhoto(result: IosPlatformActionResult)
    fun discardPendingPhoto(token: String)
    fun requestNotificationPermission()
    fun openSystemSettings()
    fun openPrivacyPolicy()
}

fun MainViewController(
    graph: IosProductionAppGraph,
    platformActions: IosPlatformActionBridge,
): UIViewController {
    val handler = IosPlatformActionHandler(platformActions)
    return ComposeUIViewController {
        App(root = graph.root, platformActions = handler, onThemeChanged = { ThemeChanged(it) })
    }
}

private class IosPlatformActionHandler(
    private val platformActions: IosPlatformActionBridge,
) : PlatformActionHandler {
    override fun perform(request: PlatformActionRequest, onResult: (String) -> Unit) {
        val result = object : IosPlatformActionResult {
            override fun fail() {
                val failure = if (request.kind == PlatformActionKind.CREATE_DOCUMENT)
                    PlatformActionFailure.DOCUMENT_DESTINATION else PlatformActionFailure.DOCUMENT_IMPORT
                request.onFailure?.invoke(failure)
            }
            override fun accept(value: String) {
                if (value.isNotBlank()) onResult(value)
            }
        }
        when (request.kind) {
            PlatformActionKind.CHOOSE_DOCUMENT -> platformActions.chooseDocument(result)
            PlatformActionKind.CREATE_DOCUMENT -> {
                val suggestedName = request.suggestedName
                val mediaType = request.mediaType
                if (!suggestedName.isNullOrBlank() && !mediaType.isNullOrBlank()) {
                    platformActions.createDocument(suggestedName, mediaType, result)
                } else {
                    result.fail()
                }
            }
            PlatformActionKind.CHOOSE_PHOTO -> platformActions.choosePhoto(object : IosPlatformActionResult {
                override fun fail() {
                    request.onFailure?.invoke(PlatformActionFailure.PHOTO_IMPORT)
                }
                override fun accept(value: String) {
                    if (value.isNotBlank()) onResult(pendingPhotoDraftValue(value))
                }
            })
            PlatformActionKind.DISCARD_PENDING_PHOTO -> request.opaqueValue
                .let(::pendingPhotoToken)
                ?.let(platformActions::discardPendingPhoto)
            PlatformActionKind.REQUEST_NOTIFICATION_PERMISSION -> platformActions.requestNotificationPermission()
            PlatformActionKind.OPEN_SYSTEM_SETTINGS -> platformActions.openSystemSettings()
            PlatformActionKind.OPEN_PRIVACY_POLICY -> platformActions.openPrivacyPolicy()
        }
    }
}

@Composable
private fun ThemeChanged(isDark: Boolean) {
    LaunchedEffect(isDark) {
        UIApplication.sharedApplication.setStatusBarStyle(
            if (isDark) UIStatusBarStyleDarkContent else UIStatusBarStyleLightContent
        )
    }
}
