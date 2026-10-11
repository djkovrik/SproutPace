import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.room)
    alias(libs.plugins.ksp)
    alias(libs.plugins.paparazzi)
}

abstract class GenerateComposablePreviewPaparazziTests : DefaultTask() {
    @get:Input
    abstract val previewPackages: ListProperty<String>

    @get:Input
    abstract val testPackageName: Property<String>

    @get:Input
    abstract val testClassName: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val packageName = testPackageName.get()
        val className = testClassName.get()
        // Package/class changes must not leave stale generated tests behind.
        outputDirectory.get().asFile.deleteRecursively()
        val source = outputDirectory.file(
            "${packageName.replace('.', '/')}/$className.kt",
        ).get().asFile
        source.parentFile.mkdirs()
        source.writeText(
            """
            package $packageName

            import android.view.ViewGroup.LayoutParams
            import androidx.compose.ui.platform.ComposeView
            import app.cash.paparazzi.Paparazzi
            import com.sedsoftware.sproutpace.compose.paparazzi.PreviewCase
            import com.sedsoftware.sproutpace.compose.paparazzi.SproutPacePaparazziPreviewRule
            import com.sedsoftware.sproutpace.compose.paparazzi.SproutPacePreviewCatalog
            import com.sedsoftware.sproutpace.compose.paparazzi.SproutPacePreviewContent
            import com.sedsoftware.sproutpace.compose.paparazzi.configureComposeResources
            import org.junit.Rule
            import org.junit.Test
            import org.junit.runner.RunWith
            import org.junit.runners.Parameterized

            @RunWith(Parameterized::class)
            internal class $className(
                private val case: PreviewCase,
            ) {
                companion object {
                    @JvmStatic
                    @Parameterized.Parameters(name = "{index}")
                    fun previews(): List<PreviewCase> = SproutPacePreviewCatalog.cases
                }

                @get:Rule
                val paparazzi: Paparazzi = SproutPacePaparazziPreviewRule.createFor(case.preview)

                @Test
                fun snapshot() {
                    paparazzi.configureComposeResources()
                    val view = ComposeView(paparazzi.context).apply {
                        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                        setContent {
                            SproutPacePreviewContent(case.preview.previewInfo) {
                                case.preview()
                            }
                        }
                    }
                    // Capture the settled static UI, not the first composition frame.
                    paparazzi.snapshot(view, name = case.id, offsetMillis = 1_000L)
                }
            }
            """.trimIndent(),
        )
    }
}

val previewPackage = "com.sedsoftware.sproutpace.compose"
val generatedPreviewTestDirectory = layout.buildDirectory.dir("generated/source/paparazziPreviews/androidHostTest/kotlin")
val generateComposablePreviewPaparazziTests = tasks.register<GenerateComposablePreviewPaparazziTests>(
    "generateComposablePreviewPaparazziTests",
) {
    group = "verification"
    description = "Generates the scanner-driven parameterized Paparazzi suite."
    previewPackages.set(listOf(previewPackage))
    // Keep Paparazzi's generated temporary image paths below the Windows MAX_PATH
    // limit. The stable snapshot identity remains case.id below.
    testPackageName.set("p")
    testClassName.set("P")
    outputDirectory.set(generatedPreviewTestDirectory)
}

kotlin {
    android {
        namespace = "com.sedsoftware.sproutpace.compose"
        compileSdk = 37
        minSdk = 23
        androidResources.enable = true
        withHostTest { isIncludeAndroidResources = true }
        compilerOptions { jvmTarget = JvmTarget.JVM_17 }
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:component"))
            implementation(project(":shared:data"))
            implementation(project(":shared:monetization"))
            api(project(":shared:platform"))
            implementation(project(":shared:root"))
            implementation(libs.decompose)
            implementation(libs.essenty.lifecycle)
            implementation(libs.mvikotlin)
            implementation(libs.mvikotlin.main)
            api(libs.compose.runtime)
            api(libs.compose.ui)
            api(libs.compose.foundation)
            api(libs.compose.resources)
            api(libs.compose.ui.tooling.preview)
            api(libs.compose.material3)
            implementation(libs.kermit)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.serialization)
            implementation(libs.ktor.serialization.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.coil)
            implementation(libs.coil.network.ktor)
            implementation(libs.multiplatformSettings)
            implementation(libs.kotlinx.datetime)
            implementation(libs.room.runtime)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.compose.ui.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.ktor.client.okhttp)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }

        getByName("androidHostTest") {
            kotlin.setSrcDirs(listOf("src/androidUnitTest/kotlin"))
            kotlin.srcDir(generateComposablePreviewPaparazziTests)
            dependencies {
                implementation(libs.sqldelight.sqlite.driver)
                implementation("com.russhwolf:multiplatform-settings-test:1.3.0")
                implementation(libs.test.junit4)
                implementation(libs.test.composable.preview.scanner.android)
                // Android KMP host tests only generate dependency R classes for
                // resources present on this source set. Paparazzi reflects over
                // every resource package, including Compose tooling-data.
                implementation(libs.compose.ui.tooling)
            }
        }

    }

    targets
        .withType<KotlinNativeTarget>()
        .matching { it.konanTarget.family.isAppleFamily }
        .configureEach {
            binaries {
                framework {
                    baseName = "compose"
                    isStatic = true
                    // Swift implements IosCrashReportingBridge from this module.
                    export(project(":shared:platform"))
                }
            }
        }
}

dependencies {
    androidRuntimeClasspath(libs.compose.ui.tooling)
}

tasks.matching {
    it.name.startsWith("cleanRecordPaparazzi") ||
        it.name.startsWith("recordPaparazzi") ||
        it.name.startsWith("verifyPaparazzi")
}.configureEach {
    dependsOn(generateComposablePreviewPaparazziTests)
    notCompatibleWithConfigurationCache(
        "Paparazzi 2.0.0-alpha05 captures non-serializable Android variant state",
    )
}

tasks.matching { it.name == "deleteAndroidMainPaparazziSnapshots" }.configureEach {
    dependsOn(generateComposablePreviewPaparazziTests)
    dependsOn("kspAndroidHostTest")
    notCompatibleWithConfigurationCache(
        "Paparazzi snapshot deletion queries KSP-generated preview metadata",
    )
}

tasks.withType<Test>().configureEach {
    reports.html.required.set(true)
    // Golden timestamps were reviewed in Europe/Moscow. Keep host tests
    // reproducible on developer machines and CI runners (which use UTC).
    systemProperty("user.timezone", "Europe/Moscow")
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    with(libs.room.compiler) {
        add("kspAndroid", this)
        add("kspIosArm64", this)
        add("kspIosSimulatorArm64", this)
    }
}
