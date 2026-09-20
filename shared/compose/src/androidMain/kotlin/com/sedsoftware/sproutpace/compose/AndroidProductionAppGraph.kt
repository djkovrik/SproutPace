package com.sedsoftware.sproutpace.compose

import android.content.Context
import android.net.Uri
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.russhwolf.settings.SharedPreferencesSettings
import com.sedsoftware.sproutpace.data.AndroidPrivateFileStore
import com.sedsoftware.sproutpace.data.AndroidPendingPhotoImportStore
import com.sedsoftware.sproutpace.data.AndroidSqlDriverFactory
import com.sedsoftware.sproutpace.data.ArchiveDocumentPort
import com.sedsoftware.sproutpace.data.ProductSettings
import com.sedsoftware.sproutpace.platform.AndroidReminderDriver
import com.sedsoftware.sproutpace.platform.AndroidNotificationPermissionService
import com.sedsoftware.sproutpace.platform.AndroidReconciliationSignalStore
import com.sedsoftware.sproutpace.platform.AndroidCrashReportingDriver
import com.sedsoftware.sproutpace.platform.ReconciliationReason
import com.sedsoftware.sproutpace.root.AppRootComponent
import com.sedsoftware.sproutpace.monetization.AdConfiguration
import com.sedsoftware.sproutpace.monetization.AdvertisingRuntime
import com.sedsoftware.sproutpace.monetization.AndroidAdvertisingRuntime
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Typed Android application entry point. Construct once from Application.onCreate. */
class AndroidProductionAppGraph(
    context: Context,
    isDebug: Boolean,
    appId: String,
    appVersion: String,
    adConfiguration: AdConfiguration,
) : AutoCloseable {
    private val applicationContext = context.applicationContext
    val notificationPermissionService = AndroidNotificationPermissionService(applicationContext)
    private val settings = SharedPreferencesSettings(
        applicationContext.getSharedPreferences(
            "${applicationContext.packageName}_preferences",
            Context.MODE_PRIVATE,
        ),
    )
    private val graph = ProductionAppGraph(
        driverFactory = AndroidSqlDriverFactory(applicationContext),
        settings = settings,
        privateFileStore = AndroidPrivateFileStore(applicationContext),
        pendingPhotoImportStore = AndroidPendingPhotoImportStore(applicationContext),
        documentPort = AndroidArchiveDocumentPort(applicationContext),
        reminderDriver = AndroidReminderDriver(applicationContext),
        notificationPermissionService = notificationPermissionService,
        crashReportingDriver = AndroidCrashReportingDriver(),
        ioDispatcher = Dispatchers.IO,
        mainContext = Dispatchers.Main.immediate,
        isDebug = isDebug,
        releaseStoreFactory = DefaultStoreFactory(),
        debugStoreFactory = DefaultStoreFactory(),
    )

    private val advertisingRuntime = AndroidAdvertisingRuntime(
        context = applicationContext,
        appId = appId,
        appVersion = appVersion,
        configuration = adConfiguration,
        productSettings = graph.productSettings,
    )
    val root: AppRootComponent = AdvertisingAppRootComponent(graph.root, advertisingRuntime)
    val productSettings: ProductSettings get() = graph.productSettings

    init {
        AndroidReconciliationSignalStore(applicationContext).consume()
            .sortedBy(ReconciliationReason::name)
            .forEach(graph::reconcile)
    }

    fun onStart() = graph.onStart()
    fun onResume() = graph.onResume()
    fun onPause() = graph.onPause()
    fun onStop() = graph.onStop()
    fun requestNotificationPermission() = graph.requestNotificationPermission()
    override fun close() {
        advertisingRuntime.close()
        graph.close()
    }
}

private class AdvertisingAppRootComponent(
    delegate: AppRootComponent,
    override val advertisingRuntime: AdvertisingRuntime,
) : AppRootComponent by delegate, AdvertisingRuntimeOwner, ThemePreferenceOwner {
    override val themePreference: StateFlow<com.sedsoftware.sproutpace.data.ThemePreference> =
        (delegate as ThemePreferenceOwner).themePreference
}

internal class AndroidArchiveDocumentPort(
    private val readDocument: suspend (Uri) -> ByteArray?,
    private val writeDocument: suspend (Uri, ByteArray) -> Unit,
    private val readImportedFile: suspend (String) -> ByteArray? = { null },
    private val isImportedFile: (String) -> Boolean = { false },
) : ArchiveDocumentPort {
    constructor(context: Context) : this(
        readDocument = { uri ->
            withContext(Dispatchers.IO) {
                context.applicationContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
        },
        writeDocument = { uri, bytes ->
            withContext(Dispatchers.IO) {
                val output = context.applicationContext.contentResolver.openOutputStream(uri, "wt")
                    ?: throw IOException("The document provider did not return an output stream")
                output.use { it.write(bytes) }
            }
        },
        readImportedFile = { path ->
            withContext(Dispatchers.IO) { File(path).takeIf { it.isFile }?.readBytes() }
        },
        isImportedFile = { path -> isCanonicalImportedFile(context.applicationContext, path) },
    )

    override suspend fun read(documentId: String): ByteArray? {
        val uri = Uri.parse(documentId)
        return when {
            uri.scheme == "content" -> readDocument(uri)
            uri.scheme == null -> {
                require(isImportedFile(documentId)) { "Android archive file must be app-owned" }
                readImportedFile(documentId)
            }
            else -> throw IllegalArgumentException("Android archive documents must use content URIs or app-owned paths")
        }
    }

    override suspend fun write(documentId: String, bytes: ByteArray) =
        writeDocument(documentId.contentUri(), bytes)

    private fun String.contentUri(): Uri = Uri.parse(this).also { uri ->
        require(uri.scheme == "content") { "Android archive documents must use content URIs" }
    }
}

private fun isCanonicalImportedFile(context: Context, path: String): Boolean = runCatching {
    val importsRoot = File(context.filesDir, "imports").canonicalFile
    val candidate = File(path).canonicalFile
    candidate.isFile && candidate.parentFile == importsRoot
}.getOrDefault(false)
