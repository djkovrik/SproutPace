import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.FailOnSeverity
import kotlinx.kover.gradle.plugin.dsl.GroupingEntityType

plugins {
    base
    alias(libs.plugins.kotlin.multiplatform).apply(false)
    alias(libs.plugins.compose.compiler).apply(false)
    alias(libs.plugins.compose.multiplatform).apply(false)
    alias(libs.plugins.android.application).apply(false)
    alias(libs.plugins.android.kmp.library).apply(false)
    alias(libs.plugins.kotlinx.serialization).apply(false)
    alias(libs.plugins.room).apply(false)
    alias(libs.plugins.ksp).apply(false)
    alias(libs.plugins.sqldelight).apply(false)
    alias(libs.plugins.paparazzi).apply(false)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

detekt {
    toolVersion = libs.versions.detekt.get()
    source.setFrom(
        fileTree(rootDir) {
            include("**/*.kt", "**/*.kts")
            exclude(
                ".gradle/**",
                ".vibe/**",
                "app/cash/paparazzi/**",
                "**/build/**",
                "**/generated/**",
                "**/src/*Test/**",
                "**/src/test/**",
            )
        },
    )
    config.setFrom(file("detekt/base-config.yml"))
    buildUponDefaultConfig = false
    parallel = true
    ignoreFailures = false
    failOnSeverity = FailOnSeverity.Warning
    basePath.set(rootDir)
}

tasks.withType<Detekt>().configureEach {
    exclude("**/build/**", "**/generated/**", "app/cash/paparazzi/**")
    reports {
        checkstyle.required.set(true)
        html.required.set(true)
        sarif.required.set(true)
    }
}

val coverageProjectPaths = listOf(
    ":shared:domain",
    ":shared:data",
    ":shared:component",
    ":shared:root",
    ":shared:platform",
    ":shared:monetization",
)

listOf(":shared:component", ":shared:root").forEach { projectPath ->
    project(projectPath).pluginManager.apply("org.jetbrains.kotlinx.kover")
}

dependencies {
    coverageProjectPaths.forEach { projectPath ->
        kover(project(projectPath))
    }
}

val sproutPaceCoverageMinimum = providers
    .gradleProperty("sproutpace.kover.minimum")
    .map { value ->
        value.toInt().also { minimum ->
            require(minimum in 1..100) {
                "sproutpace.kover.minimum must be between 1 and 100, but was $minimum"
            }
        }
    }
    .orElse(60)

kover {
    reports {
        filters {
            includes {
                packages(
                    "com.sedsoftware.sproutpace.domain",
                    "com.sedsoftware.sproutpace.data",
                    "com.sedsoftware.sproutpace.component",
                    "com.sedsoftware.sproutpace.root",
                    "com.sedsoftware.sproutpace.platform",
                    "com.sedsoftware.sproutpace.monetization",
                )
            }
            excludes {
                classes(
                    "*Preview*",
                    "com.sedsoftware.sproutpace.data.db.*",
                    "*.BuildConfig",
                    "*.Res",
                    "*.Res$*",
                )
            }
        }
        verify {
            rule("SproutPace production logic line coverage") {
                minBound(sproutPaceCoverageMinimum)
            }
            rule("SproutPace production package coverage presence") {
                groupBy = GroupingEntityType.PACKAGE
                minBound(1)
            }
        }
    }
}

val coverageHostTestTasks = coverageProjectPaths.map { projectPath ->
    "$projectPath:testAndroidHostTest"
}

tasks.named("koverXmlReport") {
    dependsOn(coverageHostTestTasks)
}

tasks.named("koverVerify") {
    dependsOn(coverageHostTestTasks)
}

tasks.named("check") {
    dependsOn("detekt", "koverVerify")
}
