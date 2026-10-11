package com.sedsoftware.sproutpace.compose

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.russhwolf.settings.MapSettings
import com.sedsoftware.sproutpace.component.LoadState
import com.sedsoftware.sproutpace.component.ScreenCommand
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.component.ScreenModel
import com.sedsoftware.sproutpace.component.TextKey
import com.sedsoftware.sproutpace.data.LocalDatabase
import com.sedsoftware.sproutpace.data.MultiplatformProductSettings
import com.sedsoftware.sproutpace.data.ProductionScreenDataManager
import com.sedsoftware.sproutpace.data.SqlDriverFactory
import com.sedsoftware.sproutpace.data.db.SproutPaceDatabase
import com.sedsoftware.sproutpace.monetization.AdConfiguration
import com.sedsoftware.sproutpace.monetization.AdHostMode
import com.sedsoftware.sproutpace.monetization.AdPlatform
import com.sedsoftware.sproutpace.monetization.AdPreparation
import com.sedsoftware.sproutpace.monetization.AdSdkDriver
import com.sedsoftware.sproutpace.monetization.AdvertisingController
import com.sedsoftware.sproutpace.monetization.EpochMillisClock
import com.sedsoftware.sproutpace.monetization.PrivacyCoordinator
import com.sedsoftware.sproutpace.monetization.PrivacyRegion
import com.sedsoftware.sproutpace.monetization.PrivacyRegionApi
import com.sedsoftware.sproutpace.monetization.PrivacyRepository
import com.sedsoftware.sproutpace.monetization.ProductSettingsPrivacyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdvertisingProductionProjectionTest {
    @Test
    fun localCatalogAndRealHistoryReachPrivacyOnlyWhenHostIsOnline() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        SproutPaceDatabase.Schema.create(driver)
        val database = LocalDatabase(object : SqlDriverFactory {
            override fun create(): SqlDriver = driver
            override fun close() = Unit
        }, Dispatchers.Unconfined)
        try {
            val settings = MultiplatformProductSettings(MapSettings())
            val manager = ProductionScreenDataManager(database, settings)
            suspend fun execute(screen: ScreenId, operation: String, id: String, vararg args: Pair<String, String>) {
                manager.execute(screen, ScreenCommand(operation, id, args.toMap()).newInvocation()).getOrThrow()
            }
            execute(ScreenId.TRAY_SIZES, "save-tray-size", "tray", "name" to "Tray", "shape" to "rectangle",
                "widthCm" to "25", "lengthCm" to "50")
            execute(ScreenId.ZONES, "save-zone", "zone", "name" to "Shelf", "type" to "grow_light", "capacity" to "10")
            repeat(6) { index ->
                val id = "batch-$index"
                execute(ScreenId.BATCH_EDITOR, "save-batch", id, "cultureId" to "pea", "trayId" to "tray",
                    "substrate" to "Coco", "plannedSowDate" to "2026-10-10", "timeZoneId" to "UTC")
                execute(ScreenId.BATCH_DETAIL, "confirm-sowing", id, "zoneId" to "zone",
                    "occurredAt" to "2026-10-10T09:00:00Z", "eventId" to "sow-$index")
                execute(ScreenId.CLOSE_BATCH, "close-failed", id, "reason" to "mold",
                    "occurredAt" to "2026-10-10T12:00:00Z")
            }
            val events = mutableListOf<String>()
            val sdk = object : AdSdkDriver {
                override val platform = AdPlatform.ANDROID
                override val supported = true
                override suspend fun setUserConsent(consent: Boolean) { events += "consent:$consent" }
                override suspend fun initialize() { events += "initialize" }
                override suspend fun stopNewRequestsAndDispose() = Unit
            }
            val controller = AdvertisingController(PrivacyCoordinator(
                PrivacyRegionApi { Result.success(PrivacyRegion(1, true, "policy-1", 100L, 200L)) },
                PrivacyRepository(ProductSettingsPrivacyStore(settings)), EpochMillisClock { 100L },
            ), sdk, AdConfiguration(AdPlatform.ANDROID, AdHostMode.DEBUG, null))
            for ((screen, slot) in listOf(ScreenId.CATALOG to 4, ScreenId.TRAYS to 5)) {
                val snapshot = manager.watch(screen).first()
                val model = ScreenModel(screen, LoadState.CONTENT, TextKey("title"), items = snapshot.items,
                    offline = snapshot.offline, fields = snapshot.fields.map {
                        if (it.id.value == "category") it.copy(value = "history") else it
                    })
                val ui = model.toUiState()
                assertTrue(ui.offline)
                assertNull(ui.adPlacementRequestOrNull(networkAvailable = false))
                val request = assertNotNull(ui.adPlacementRequestOrNull(networkAvailable = true))
                assertEquals(slot, request.slotIndex)
                if (screen == ScreenId.CATALOG) {
                    assertIs<AdPreparation.ConsentRequired>(controller.prepare(request))
                    assertTrue(events.isEmpty())
                    assertTrue(controller.acceptConsent())
                } else assertEquals(6, request.realItemCount)
                assertIs<AdPreparation.Ready>(controller.prepare(request))
            }
            assertEquals(listOf("consent:true", "initialize"), events)
        } finally {
            database.close()
            driver.close()
        }
    }
}
