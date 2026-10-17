package com.sedsoftware.sproutpace.compose

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.russhwolf.settings.MapSettings
import com.sedsoftware.sproutpace.component.*
import com.sedsoftware.sproutpace.component.integration.ScreenComponentDependencies
import com.sedsoftware.sproutpace.component.integration.ScreenComponentFactory
import com.sedsoftware.sproutpace.data.*
import com.sedsoftware.sproutpace.data.db.SproutPaceDatabase
import com.sedsoftware.sproutpace.domain.model.ProductSnapshot
import com.sedsoftware.sproutpace.root.AppRootComponentDefault
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.coroutines.CoroutineContext

/** Real public router/Store/manager and SQLite/photo/document persistence. No supplied screen results. */
internal class PublicProductionFixture(
    val directory: Path,
    preferences: MapSettings = MapSettings(),
    val now: () -> Instant = { Instant.parse("2026-10-10T10:00:00Z") },
    val timeZone: () -> TimeZone = { TimeZone.UTC },
    initialScreen: ScreenId = ScreenId.TRAYS,
    mainContext: CoroutineContext = Dispatchers.Unconfined,
    decorateManager: (ScreenDataManager) -> ScreenDataManager = { it },
    decorateManagerWithSettings: ((ScreenDataManager, ProductSettings) -> ScreenDataManager)? = null,
) : AutoCloseable {
    private val databasePath = directory.resolve("workspace.db")
    private val initialize = !Files.exists(databasePath)
    private val driver = JdbcSqliteDriver("jdbc:sqlite:${databasePath.toAbsolutePath()}",
        Properties().apply { setProperty("foreign_keys", "true") }).also {
        if (initialize) SproutPaceDatabase.Schema.create(it)
    }
    val database = LocalDatabase(object : SqlDriverFactory {
        override fun create(): SqlDriver = driver
        override fun close() = Unit
    }, Dispatchers.Unconfined)
    val settings = MultiplatformProductSettings(preferences)
    val files = AndroidPrivateFileStore(directory.resolve("photos").toFile())
    val pendingPhotos = InMemoryPendingPhotoImportStore()
    val documents = PublicFileDocuments(directory.resolve("documents"))
    val reconciledSnapshots = mutableListOf<ProductSnapshot>()
    val backup: LocalBackupService = LocalBackupService(database, files, settings, Dispatchers.Unconfined,
        documents = documents, now = now,
        reminders = ReminderReconciliationPort { reconciledSnapshots += it },
        clearPendingPhotos = { pendingPhotos.clear() },
        output = LocalDataIntegrationOutput { root.resetToFirstRun() })
    val manager = ProductionScreenDataManager(database, settings,
        workspaceRepository = LocalWorkspaceRepository(database, files, Dispatchers.Unconfined),
        backupService = backup, pendingPhotos = pendingPhotos, now = now,
        today = { now().toLocalDateTime(timeZone()).date }, timeZone = timeZone)
    val lifecycle = LifecycleRegistry().apply { resume() }
    val root: AppRootComponentDefault = AppRootComponentDefault(DefaultComponentContext(lifecycle), ScreenComponentFactory(
        ScreenComponentDependencies(DefaultStoreFactory(),
            decorateManagerWithSettings?.invoke(manager, settings) ?: decorateManager(manager), mainContext)), initialScreen)
    val platformRequests = mutableListOf<PlatformActionRequest>()
    var selectedDocument = "imported"
    var exportDocument = "exported"
    var selectedPhoto = "picker-photo"
    var platformHandler = PlatformActionHandler { request, result ->
        platformRequests += request
        when (request.kind) {
            PlatformActionKind.CHOOSE_DOCUMENT -> result(selectedDocument)
            PlatformActionKind.CREATE_DOCUMENT -> result(exportDocument)
            PlatformActionKind.CHOOSE_PHOTO -> result(selectedPhoto)
            else -> Unit
        }
    }

    fun model() = root.stack.value.active.instance.model.value
    fun field(id: String) = model().fields.single { it.id.value == id }.value
    fun dispatch(action: UiAction) {
        val active = root.stack.value.active.instance
        RootActionRouter(root, active, platformHandler) { active.model.value.selectedItemId }.dispatch(action)
    }
    /** Start a fresh public screen visit, avoiding duplicate back-stack configurations. */
    fun open(screen: ScreenId, id: String? = null) {
        dispatch(UiAction.Navigate(UiScreen.TRAYS))
        if (screen != ScreenId.TRAYS) root.open(screen, id)
    }
    fun edit(fields: Map<String, String>) = fields.forEach { (id, value) -> dispatch(UiAction.EditField(id, value)) }
    fun submit(operation: String, subject: String? = null, confirm: Boolean = true) {
        dispatch(UiAction.Submit(operation, subject))
        if (confirm) root.confirmation.value.child?.instance?.confirm()
    }
    fun save(screen: ScreenId, id: String?, operation: String, fields: Map<String, String>) {
        open(screen, id)
        edit(fields)
        submit(operation)
    }
    suspend fun seed() {
        manager.execute(ScreenId.ZONES, ScreenCommand("save-zone", "zone", mapOf(
            "name" to "Shelf", "type" to "grow_light", "capacity" to "2")).newInvocation()).getOrThrow()
        manager.execute(ScreenId.TRAY_SIZES, ScreenCommand("save-tray-size", "tray", mapOf(
            "name" to "Tray", "shape" to "rectangle", "widthCm" to "20", "lengthCm" to "30")).newInvocation()).getOrThrow()
        manager.execute(ScreenId.BATCH_EDITOR, ScreenCommand("save-batch", "batch", mapOf(
            "cultureId" to "pea", "trayId" to "tray", "substrate" to "Coco", "actualSeedMassGrams" to "12.5",
            "plannedSowDate" to "2026-10-10")).newInvocation()).getOrThrow()
    }
    override fun close() { lifecycle.destroy(); database.close(); files.close(); pendingPhotos.close() }
}

/** File-backed test host port: external exports survive private import cleanup. */
internal class PublicFileDocuments(private val directory: Path) : ArchiveDocumentPort {
    private fun path(id: String): Path {
        require(id.matches(Regex("[A-Za-z0-9._-]+")))
        return directory.resolve(if (id.startsWith("imported")) "imports" else "exports").resolve(id)
    }
    override suspend fun read(documentId: String): ByteArray? = path(documentId).takeIf(Files::exists)?.let(Files::readAllBytes)
    override suspend fun write(documentId: String, bytes: ByteArray) {
        val file = path(documentId)
        Files.createDirectories(file.parent)
        Files.write(file, bytes)
    }
    override suspend fun clearImportedArchives() {
        val imports = directory.resolve("imports")
        if (Files.exists(imports)) Files.list(imports).use { entries -> entries.forEach(Files::delete) }
    }
}

internal suspend fun withPublicWorkspace(block: suspend (Path, MapSettings) -> Unit) {
    val directory = Files.createTempDirectory("sproutpace-public-")
    try { block(directory, MapSettings()) } finally {
        Files.walk(directory).use { entries -> entries.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
    }
}
