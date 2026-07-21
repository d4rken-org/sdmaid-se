import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension

plugins {
    id("projectConfig")
    id("com.google.devtools.ksp") version "2.3.6" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:9.3.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.Kotlin.core}")
        classpath("com.google.dagger:hilt-android-gradle-plugin:${Versions.Dagger.core}")
        classpath("org.jetbrains.kotlin:kotlin-serialization:${Versions.Kotlin.core}")
        classpath("org.jetbrains.kotlinx:kover-gradle-plugin:0.9.8")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
    }
}

// --- Code coverage (Kover) -------------------------------------------------------------------
// Aggregates unit-test coverage for the core library modules into one merged report. Run:
//   ./gradlew :koverHtmlReport       (merged HTML, at build/reports/kover/html/index.html)
//   ./gradlew :koverXmlReport        (merged XML, for CI)
//   ./gradlew :koverPrintCoverage    (prints the merged % to the console)
// Per-module reports use the variant-suffixed tasks instead, e.g.
//   ./gradlew :app-common:koverHtmlReportDebug
// The package-scoped coverage gate lives in app-common/build.gradle.kts (koverVerifyDebug).
apply(plugin = "org.jetbrains.kotlinx.kover")

dependencies {
    "kover"(project(":app-common"))
    "kover"(project(":app-common-shell"))
    "kover"(project(":app-common-io"))
    "kover"(project(":app-common-root"))
    "kover"(project(":app-common-adb"))
}

configure<KoverProjectExtension> {
    reports {
        filters {
            excludes {
                // Generated / boilerplate — not meaningful to measure.
                annotatedBy(
                    "dagger.Module",
                    "dagger.internal.DaggerGenerated",
                    "javax.annotation.processing.Generated",
                )
                classes(
                    "*_Factory", "*_Factory\$*", "*_MembersInjector",
                    "Hilt_*", "*.Hilt_*", "*.Dagger*", "hilt_aggregated_deps.*", "*_HiltModules*",
                    "*.BuildConfig", "*.R", "*.R\$*", "*.databinding.*",
                    // AIDL-generated binder code (Stub / Stub$Proxy / Default) across all modules.
                    "*\$Stub", "*\$Stub\$*", "*\$Default",
                )
            }
        }
    }
}

tasks.register("clean").configure {
    delete("build")
}

tasks.register("testToolModules") {
    description = "Run unit tests for all app-tool-* library modules"
    dependsOn(subprojects.filter { it.name.startsWith("app-tool") }.map { ":${it.name}:testDebugUnitTest" })
}

tasks.register("testCommonModules") {
    description = "Run unit tests for all app-common-* library modules"
    dependsOn(subprojects.filter { it.name.startsWith("app-common") }.map { ":${it.name}:testDebugUnitTest" })
}