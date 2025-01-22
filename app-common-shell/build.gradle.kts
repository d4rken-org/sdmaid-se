plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlin-parcelize")
    id("projectConfig")
}

apply(plugin = "dagger.hilt.android.plugin")

android {
    namespace = "${projectConfig.packageName}.common.shell"

    setupLibraryDefaults(projectConfig)

    setupModuleBuildTypes()

    buildFeatures {
        viewBinding = true
    }

    setupCompileOptions()

    setupKotlinOptions()

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
        tasks.withType<Test> {
            useJUnitPlatform()
            setupTestLogging()
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:${Versions.Desugar.core}")
    implementation(project(":app-common"))

    addAndroidCore()
    addDI()
    addCoroutines()

    addTesting()
    testImplementation(project(":app-common-test"))
    testImplementation("org.robolectric:robolectric:4.9.1")
    testImplementation("androidx.test.ext:junit:1.1.4")
}