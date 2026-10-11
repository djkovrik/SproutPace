package com.sedsoftware.sproutpace.compose

import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.sedsoftware.sproutpace.component.EmptyScreenDataManager
import com.sedsoftware.sproutpace.data.LocalDataIntegrationEvent
import com.sedsoftware.sproutpace.domain.catalog.SproutPaceCatalog
import com.sedsoftware.sproutpace.domain.model.BackupManifest
import com.sedsoftware.sproutpace.domain.model.BatchId
import com.sedsoftware.sproutpace.domain.model.DueMoment
import com.sedsoftware.sproutpace.domain.model.GrowBatch
import com.sedsoftware.sproutpace.domain.model.GrowStage
import com.sedsoftware.sproutpace.domain.model.Placement
import com.sedsoftware.sproutpace.domain.model.PositiveGrams
import com.sedsoftware.sproutpace.domain.model.ProductSnapshot
import com.sedsoftware.sproutpace.domain.model.RecipeSnapshot
import com.sedsoftware.sproutpace.domain.model.ScheduledAction
import com.sedsoftware.sproutpace.domain.model.ScheduledActionId
import com.sedsoftware.sproutpace.domain.model.TrayShape
import com.sedsoftware.sproutpace.domain.model.TraySnapshot
import com.sedsoftware.sproutpace.platform.FakeReminderDriver
import com.sedsoftware.sproutpace.platform.PhysicalReminder
import com.sedsoftware.sproutpace.platform.PhysicalReminderId
import com.sedsoftware.sproutpace.platform.ReminderKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ProductionAppGraphContractTest {
    @Test
    fun restoreReminderPortPropagatesPhysicalRegistrationFailure() = runTest {
        val reminder = PhysicalReminder(PhysicalReminderId("sproutpace:daily-summary"), ReminderKind.DAILY_SUMMARY,
            20_000, com.sedsoftware.sproutpace.platform.NativeTextKey.DAILY_SUMMARY_TITLE,
            com.sedsoftware.sproutpace.platform.NativeTextKey.DAILY_SUMMARY_BODY)
        val driver = object : com.sedsoftware.sproutpace.platform.ReminderDriver {
            override suspend fun registeredIds(): Set<PhysicalReminderId> = emptySet()
            override suspend fun schedule(reminder: PhysicalReminder): Result<Unit> = Result.failure(IllegalStateException("full"))
            override suspend fun cancel(id: PhysicalReminderId): Result<Unit> = Result.success(Unit)
        }
        val coordinator = com.sedsoftware.sproutpace.platform.ReminderLifecycleCoordinator(
            com.sedsoftware.sproutpace.platform.FakeNotificationPermissionService(
                com.sedsoftware.sproutpace.platform.PermissionState.Granted),
            com.sedsoftware.sproutpace.platform.ReminderPlanSource {
                com.sedsoftware.sproutpace.platform.ReminderPlan(1, reminder, emptyList())
            }, com.sedsoftware.sproutpace.platform.ReminderReconciler(driver),
            com.sedsoftware.sproutpace.platform.EpochMillisClock { 10_000 })
        val failure = kotlin.test.assertFailsWith<com.sedsoftware.sproutpace.platform.ReminderReconciliationException> {
            SnapshotReminderReconciliationPort(coordinator).replaceFutureReminders(snapshotWithSoakTarget(20_000))
        }
        assertEquals(setOf(reminder.id), failure.failures.keys)
    }

    @Test
    fun rootDependenciesKeepReleaseAndDebugFactoriesDistinct() {
        val releaseFactory = DefaultStoreFactory()
        val debugFactory = DefaultStoreFactory()

        val release = productionRootDependencies(
            releaseStoreFactory = releaseFactory,
            debugStoreFactory = debugFactory,
            isDebug = false,
            screenDataManager = EmptyScreenDataManager,
            mainContext = Dispatchers.Unconfined,
        )
        val debug = productionRootDependencies(
            releaseStoreFactory = releaseFactory,
            debugStoreFactory = debugFactory,
            isDebug = true,
            screenDataManager = EmptyScreenDataManager,
            mainContext = Dispatchers.Unconfined,
        )

        assertFalse(release.isDebug)
        assertTrue(debug.isDebug)
        assertSame(releaseFactory, release.releaseStoreFactory)
        assertSame(debugFactory, release.debugStoreFactory)
        assertSame(releaseFactory, debug.releaseStoreFactory)
        assertSame(debugFactory, debug.debugStoreFactory)
    }

    @Test
    fun dependencyContractTestDoesNotConstructProductionStorage() {
        val dependencies = productionRootDependencies(
            releaseStoreFactory = DefaultStoreFactory(),
            debugStoreFactory = DefaultStoreFactory(),
            isDebug = false,
            screenDataManager = EmptyScreenDataManager,
            mainContext = Dispatchers.Unconfined,
        )

        assertSame(EmptyScreenDataManager, dependencies.screenDataManager)
    }

    @Test
    fun reminderReconciliationCancelsObsoleteAndSchedulesOnlyFutureSoakTargets() = runTest {
        val obsolete = PhysicalReminder(
            PhysicalReminderId("sproutpace:soak:old:old"),
            ReminderKind.SOAK,
            20_000,
            com.sedsoftware.sproutpace.platform.NativeTextKey.SOAK_COMPLETE_TITLE,
            com.sedsoftware.sproutpace.platform.NativeTextKey.SOAK_COMPLETE_BODY,
        )
        val driver = FakeReminderDriver(listOf(obsolete))
        val port = SnapshotReminderReconciliationPort(driver) { 10_000 }

        port.replaceFutureReminders(snapshotWithSoakTarget(20_000))

        assertEquals(listOf(obsolete.id), driver.cancelledCalls)
        assertEquals(1, driver.scheduledCalls.size)
        assertEquals(20_000, driver.scheduledCalls.single().fireAtEpochMillis)
        assertEquals(ReminderKind.SOAK, driver.scheduledCalls.single().kind)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun eraseOutputSwitchesToMainContextBeforeResettingRoot() = runTest {
        val main = StandardTestDispatcher(testScheduler, name = "production-main")
        val events = mutableListOf<String>()
        val output = FirstRunNavigationOutput(main) { events += "reset-root" }

        events += "commit-complete"
        output.emit(LocalDataIntegrationEvent.FirstRunNavigationRequired)

        assertEquals(listOf("commit-complete", "reset-root"), events)
    }

    private fun snapshotWithSoakTarget(fireAtEpochMillis: Long): ProductSnapshot {
        val culture = SproutPaceCatalog.cultures.first()
        val batchId = BatchId("batch-one")
        val batch = GrowBatch(
            id = batchId,
            recipe = RecipeSnapshot(null, culture.id, null, culture.stages.map {
                com.sedsoftware.sproutpace.domain.model.RecipeStage(it.stage, it.duration, it.instructionKey)
            }, culture.seedDensity),
            tray = TraySnapshot("Test tray", TrayShape.Rectangle(10.0, 20.0)),
            substrateSnapshot = "Test substrate",
            recommendedSeedMass = PositiveGrams(10.0),
            actualSeedMass = PositiveGrams(10.0),
            state = com.sedsoftware.sproutpace.domain.model.BatchState.SOAKING,
            placement = Placement.None,
            events = emptyList(),
            actions = listOf(ScheduledAction(
                id = ScheduledActionId("soak-one"),
                batchId = batchId,
                stage = GrowStage.SOAKING,
                due = DueMoment.ElapsedTarget(Instant.fromEpochMilliseconds(fireAtEpochMillis)),
            )),
        )
        return ProductSnapshot(
            BackupManifest(1, 1, emptyMap(), emptyMap()),
            emptyList(), emptyList(), emptyList(), listOf(batch), emptyList(), emptyList(),
        )
    }
}
