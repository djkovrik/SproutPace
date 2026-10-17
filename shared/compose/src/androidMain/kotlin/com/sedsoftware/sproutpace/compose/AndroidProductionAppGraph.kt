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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.InputStream
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
    private val preferences = applicationContext.getSharedPreferences(
        "${applicationContext.packageName}_preferences", Context.MODE_PRIVATE,
    )
    private val settings = SharedPreferencesSettings(preferences, commit = true)
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
        dailyEvaluationEnabled = true,
        flushPreferences = { check(preferences.edit().commit()) { "preferences_flush_failed" } },
    )

    private val advertisingRuntime = AndroidAdvertisingRuntime(
        context = applicationContext,
        appId = appId,
        appVersion = appVersion,
        configuration = adConfiguration,
        productSettings = graph.productSettings,
        workspaceReady = graph.workspaceReady,
    )
    val root: AppRootComponent = AdvertisingAppRootComponent(graph.root, advertisingRuntime)
    val productSettings: ProductSettings get() = graph.productSettings

    private val reconciliationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        val signals = AndroidReconciliationSignalStore(applicationContext)
        reconciliationScope.launch {
            signals.pending().sortedBy(ReconciliationReason::name).forEach { reason ->
                runCatching { graph.reconcileReminderEnvironment(reason) }
                    .onSuccess { signals.acknowledge(reason) }
            }
        }
    }

    suspend fun reconcileReminderEnvironment(reason: ReconciliationReason) = graph.reconcileReminderEnvironment(reason)
    suspend fun evaluateReminderDelivery(id: com.sedsoftware.sproutpace.platform.PhysicalReminderId, fireAt: Long): Boolean =
        graph.evaluateReminderDelivery(id, fireAt)

    fun onStart() = graph.onStart()
    fun onResume() = graph.onResume()
    fun onPause() = graph.onPause()
    fun onStop() = graph.onStop()
    fun requestNotificationPermission() = graph.requestNotificationPermission()
    override fun close() {
        reconciliationScope.cancel()
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
    private val clearImports: suspend () -> Unit = {},
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ArchiveDocumentPort {
    constructor(context: Context) : this(
        readDocument = { uri ->
            context.applicationContext.contentResolver.openInputStream(uri)?.use { it.readArchiveBytes() }
        },
        writeDocument = { uri, bytes ->
            val output = context.applicationContext.contentResolver.openOutputStream(uri, "wt")
                ?: throw IOException("The document provider did not return an output stream")
            output.use { stream ->
                var offset = 0
                while (offset < bytes.size) {
                    currentCoroutineContext().ensureActive()
                    val length = minOf(DEFAULT_BUFFER_SIZE, bytes.size - offset)
                    stream.write(bytes, offset, length)
                    offset += length
                }
            }
        },
        readImportedFile = { path ->
            File(path).takeIf { it.isFile }?.inputStream()?.use { it.readArchiveBytes() }
        },
        isImportedFile = { path -> isCanonicalImportedFile(context.applicationContext, path) },
        clearImports = { clearAndroidImportedArchives(File(context.applicationContext.filesDir, "imports")) },
    )

    override suspend fun read(documentId: String): ByteArray? = withContext(ioDispatcher) {
        currentCoroutineContext().ensureActive()
        val uri = Uri.parse(documentId)
        val bytes = when {
            uri.scheme == "content" -> readDocument(uri)
            uri.scheme == null -> {
                require(isImportedFile(documentId)) { "Android archive file must be app-owned" }
                readImportedFile(documentId)
            }
            else -> throw IllegalArgumentException("Android archive documents must use content URIs or app-owned paths")
        }
        currentCoroutineContext().ensureActive()
        bytes
    }

    override suspend fun write(documentId: String, bytes: ByteArray) = withContext(ioDispatcher) {
        currentCoroutineContext().ensureActive()
        writeDocument(documentId.contentUri(), bytes)
        currentCoroutineContext().ensureActive()
    }

    override suspend fun clearImportedArchives() = withContext(ioDispatcher) {
        currentCoroutineContext().ensureActive()
        clearImports()
    }

    private fun String.contentUri(): Uri = Uri.parse(this).also { uri ->
        require(uri.scheme == "content") { "Android archive documents must use content URIs" }
    }
}

private suspend fun InputStream.readArchiveBytes(): ByteArray {
    val result = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        currentCoroutineContext().ensureActive()
        val count = read(buffer)
        if (count < 0) break
        result.write(buffer, 0, count)
    }
    return result.toByteArray()
}

internal fun clearAndroidImportedArchives(imports: File) {
    // A symlink must never turn the app-owned cleanup root into an external document directory.
    val canonicalRoot = imports.canonicalFile
    require(canonicalRoot.parentFile == imports.parentFile.canonicalFile) {
        "Archive import directory must be app-owned"
    }
    if (!canonicalRoot.exists()) return
    val files = checkNotNull(canonicalRoot.listFiles()) { "Unable to inspect imported archives" }
    files.forEach { file ->
        require(file.canonicalFile.parentFile == canonicalRoot && file.isFile) {
            "Imported archive must remain app-owned"
        }
        check(file.delete()) { "Unable to clear imported archive" }
    }
}

private fun isCanonicalImportedFile(context: Context, path: String): Boolean = runCatching {
    val importsRoot = File(context.filesDir, "imports").canonicalFile
    val candidate = File(path).canonicalFile
    candidate.isFile && candidate.parentFile == importsRoot
}.getOrDefault(false)
