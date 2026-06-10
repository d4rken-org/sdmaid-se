import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension

plugins {
    id("com.android.library")
    id("kotlin-parcelize")
    id("com.google.devtools.ksp")
    id("projectConfig")
}

apply(plugin = "dagger.hilt.android.plugin")
apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
apply(plugin = "org.jetbrains.kotlinx.kover")

android {
    namespace = "${projectConfig.packageName}.common"

    setupLibraryDefaults(projectConfig)

    setupModuleBuildTypes()

    setupCompileOptions()

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
        tasks.withType<Test> {
            useJUnitPlatform()
            setupTests()
        }
    }
}

setupKotlinOptions()

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:${Versions.Desugar.core}")
    testImplementation(project(":app-common-test"))

    addAndroidCore()
    addDI()
    addCoroutines()
    addSerialization()
    api("androidx.navigation:navigation-common:${Versions.AndroidX.Navigation.core}")
    api("androidx.navigation3:navigation3-runtime-android:${Versions.AndroidX.Navigation3.core}")
    addIO()
    addTesting()

    implementation("io.github.z4kn4fein:semver:3.0.0")
}

// Kover's verify rules can't filter per-rule (only the report can), so to gate ONLY the
// well-covered SharedResource concurrency code we scope THIS module's own Kover report to that
// package and enforce a floor on it. The broad cross-module aggregate lives in the root build and
// is unaffected (it re-aggregates raw coverage with its own filters).
configure<KoverProjectExtension> {
    reports {
        filters { includes { classes("eu.darken.sdmse.common.sharedresource.*") } }
        verify {
            rule("SharedResource line coverage") {
                // Currently ~86%; floor leaves headroom but still guards against the concurrency
                // code rotting.
                minBound(80)
            }
        }
    }
}
