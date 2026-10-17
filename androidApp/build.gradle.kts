import java.net.URI
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.google.gms:google-services:4.5.0")
        classpath("com.google.firebase:firebase-crashlytics-gradle:3.0.8")
    }
}

plugins {
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.application)
}

val firebaseConfigFile = layout.projectDirectory.file("google-services.json").asFile
val firebaseCrashlyticsEnabled =
    providers.gradleProperty("sproutpace.firebase.enabled").orNull == "true" && firebaseConfigFile.isFile
val yandexProductionAdUnitId = providers.gradleProperty("sproutpace.yandex.android.adUnitId").orElse("")
val privacyConfigurationFile = providers.gradleProperty("sproutpace.privacy.propertiesFile").orElse("")
val publicationSigningStoreFile = providers.gradleProperty("sproutpace.signing.storeFile").orElse("")
val publicationSigningStorePassword = providers.gradleProperty("sproutpace.signing.storePassword").orElse("")
val publicationSigningKeyAlias = providers.gradleProperty("sproutpace.signing.keyAlias").orElse("")
val publicationSigningKeyPassword = providers.gradleProperty("sproutpace.signing.keyPassword").orElse("")

val configuredPrivacyPolicyUrl = providers.provider {
    val path = privacyConfigurationFile.get().trim()
    if (path.isEmpty()) return@provider ""
    val file = rootProject.file(path)
    if (!file.isFile) return@provider ""
    val value = runCatching {
        Properties().apply { file.bufferedReader(Charsets.UTF_8).use(::load) }
            .getProperty("privacyPolicy.url")
            .orEmpty()
            .trim()
    }.getOrDefault("")
    val uri = runCatching { URI(value) }.getOrNull()
    value.takeIf {
        uri?.scheme.equals("https", ignoreCase = true) &&
            !uri?.host.isNullOrBlank() &&
            !it.contains("REPLACE_WITH", ignoreCase = true)
    }.orEmpty()
}

if (firebaseCrashlyticsEnabled) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

android {
    namespace = "com.sedsoftware.sproutpace"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        targetSdk = 37

        applicationId = "com.sedsoftware.sproutpace"
        versionCode = 1
        versionName = "1.0.0"
        val privacyUrl = configuredPrivacyPolicyUrl.get()
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"$privacyUrl\"")
    }

    buildFeatures { buildConfig = true }

    buildTypes {
        debug {
            buildConfigField("String", "YANDEX_AD_UNIT_ID", "\"demo-banner-yandex\"")
            buildConfigField("boolean", "YANDEX_ADS_DEBUG", "true")
        }
        release {
            val escapedId = yandexProductionAdUnitId.get().replace("\\", "\\\\").replace("\"", "\\\"")
            buildConfigField("String", "YANDEX_AD_UNIT_ID", "\"$escapedId\"")
            buildConfigField("boolean", "YANDEX_ADS_DEBUG", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":shared:compose"))
    implementation(project(":shared:monetization"))
    implementation(project(":shared:platform"))
    implementation(project(":shared:root"))
    implementation(libs.androidx.activityCompose)
    testImplementation(libs.test.junit4)
    testImplementation(libs.mvikotlin)
    testImplementation(libs.mvikotlin.main)
    testImplementation(libs.kotlinx.coroutines.core)
    if (firebaseCrashlyticsEnabled) {
        implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
        implementation("com.google.firebase:firebase-crashlytics")
    }
}

val validateYandexReleaseAdUnit by tasks.registering {
    group = "verification"
    description = "Validates the external Yandex ID before an advertising-enabled publication."
    doLast {
        val value = yandexProductionAdUnitId.get().trim()
        check(value.matches(Regex("R-M-[0-9]+-[0-9]+"))) {
            "Set -Psproutpace.yandex.android.adUnitId=R-M-<digits>-<digits> before publication."
        }
        check(value != "demo-banner-yandex") { "The Yandex demo ad unit is forbidden in release builds." }
    }
}

val validateAndroidPublicationReadiness by tasks.registering {
    group = "verification"
    description = "Validates external Android publication values without signing or publishing an artifact."
    dependsOn(validateYandexReleaseAdUnit)

    doLast {
        val privacyPath = privacyConfigurationFile.get().trim()
        check(privacyPath.isNotEmpty()) {
            "Set -Psproutpace.privacy.propertiesFile to the approved UTF-8 privacy properties file."
        }
        val privacyFile = rootProject.file(privacyPath)
        check(privacyFile.isFile) { "Privacy properties file does not exist: $privacyPath" }
        check(
            privacyFile.canonicalFile !=
                rootProject.file("docs/configuration/advertising/privacy-policy.example.properties").canonicalFile,
        ) { "The repository example is a template, not approved publication copy." }

        val privacy = Properties().apply {
            privacyFile.bufferedReader(Charsets.UTF_8).use(::load)
        }
        val requiredPrivacyKeys = listOf(
            "privacyPolicy.url",
            "consent.en.title",
            "consent.en.body",
            "consent.en.accept",
            "consent.en.decline",
            "consent.en.policyLink",
            "consent.en.withdraw",
            "consent.ru.title",
            "consent.ru.body",
            "consent.ru.accept",
            "consent.ru.decline",
            "consent.ru.policyLink",
            "consent.ru.withdraw",
            "disclosure.en.adLabel",
            "disclosure.ru.adLabel",
        )
        requiredPrivacyKeys.forEach { key ->
            val value = privacy.getProperty(key).orEmpty().trim()
            check(value.isNotEmpty() && !value.contains("REPLACE_WITH", ignoreCase = true)) {
                "Approved privacy configuration is missing a final value for '$key'."
            }
        }
        val privacyPolicyUri = runCatching { URI(privacy.getProperty("privacyPolicy.url").trim()) }.getOrNull()
        check(privacyPolicyUri?.let { it.scheme == "https" && !it.host.isNullOrBlank() } == true) {
            "privacyPolicy.url must be a final public HTTPS URL."
        }

        val signingStorePath = publicationSigningStoreFile.get().trim()
        check(signingStorePath.isNotEmpty()) {
            "Set -Psproutpace.signing.storeFile to the external Android upload keystore."
        }
        check(rootProject.file(signingStorePath).isFile) {
            "Android upload keystore does not exist: $signingStorePath"
        }
        mapOf(
            "sproutpace.signing.storePassword" to publicationSigningStorePassword.get(),
            "sproutpace.signing.keyAlias" to publicationSigningKeyAlias.get(),
            "sproutpace.signing.keyPassword" to publicationSigningKeyPassword.get(),
        ).forEach { (property, value) ->
            check(value.isNotBlank()) { "Set -P$property before publication." }
        }
    }
}
