package com.sedsoftware.sproutpace.compose

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.detectEnvironment
import com.sedsoftware.sproutpace.compose.paparazzi.configureComposeResources
import com.sedsoftware.sproutpace.domain.catalog.SproutPaceCatalog
import com.sedsoftware.sproutpace.data.ConsentChoice
import java.util.Locale
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.getString
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/** Loads generated production resources through Compose's Android reader and system-locale resolver. */
class LocaleResourceResolutionTest {
    @get:Rule
    val paparazzi = Paparazzi(
        environment = detectEnvironment().copy(compileSdkVersion = 37),
        deviceConfig = DeviceConfig.PIXEL_5,
    )

    @Test
    fun systemLocaleChangesSelectRussianAndUnsupportedLocaleFallsBackToEnglish() = runTest {
        paparazzi.configureComposeResources()
        val previous = Locale.getDefault()
        val previousDisplay = Locale.getDefault(Locale.Category.DISPLAY)
        val previousFormat = Locale.getDefault(Locale.Category.FORMAT)
        val privacy = typedPreviewState(UiScreen.ADVERTISING_PRIVACY)
        val consentKeys = ConsentChoice.entries.map { choice -> requireNotNull(privacy
            .withAdvertisingPrivacy(previewPrivacy(choice), PREVIEW_PRIVACY_NOW).items.single { it.id == "privacy" }.stateKey) }
        val keys = listOf("nav_today", SproutPaceCatalog.cultures.single { it.id.value == "radish" }.nameKey.value,
            "privacy_heading") + consentKeys
        suspend fun resolve(languageTag: String): List<String> {
            Locale.setDefault(Locale.forLanguageTag(languageTag))
            return keys.map { getString(strictTextResource(it)) }
        }
        try {
            val english = resolve("en-US")
            assertEquals(listOf("Today", "Radish", "Your advertising choice", "Not decided", "Accepted", "Declined", "Withdrawn"), english)
            val russian = resolve("ru-RU")
            assertEquals(listOf("Сегодня", "Редис", "Ваш выбор рекламы", "Выбор не сделан", "Принято", "Отклонено", "Отозвано"), russian)
            assertEquals(english, resolve("de-DE"))
            assertEquals(english, resolve("en-GB"))
            assertEquals(russian, resolve("ru-KZ"))
            assertEquals(english, resolve("en-US"))
        } finally {
            Locale.setDefault(previous)
            Locale.setDefault(Locale.Category.DISPLAY, previousDisplay)
            Locale.setDefault(Locale.Category.FORMAT, previousFormat)
        }
    }

    @Test
    fun nativeNotificationResourcesFollowConfigurationAndFallBackToEnglish() {
        fun resolve(languageTag: String): String {
            paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5.copy(locale = languageTag.replace("-", "-r")))
            return paparazzi.context.getString(com.sedsoftware.sproutpace.platform.R.string.notification_soak_complete_title)
        }
        assertEquals("Soaking is complete", resolve("en-US"))
        assertEquals("Замачивание завершено", resolve("ru-RU"))
        assertEquals("Soaking is complete", resolve("de-DE"))
        assertEquals("Замачивание завершено", resolve("ru-KZ"))
        assertEquals("Soaking is complete", resolve("en-US"))
    }
}
