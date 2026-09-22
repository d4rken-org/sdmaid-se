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
    namespace = "${projectConfig.packageName}.common.io"

    setupLibraryDefaults(projectConfig)

    setupModuleBuildTypes()

    buildFeatures {
        aidl = true
    }

    setupCompileOptions()


    testOptions {
        // Left unset, a library's test APK inherits compileSdk (AGP 9's
        // android.sdk.defaultTargetSdkToCompileSdkIfUnset). Hidden-API enforcement is keyed on the
        // caller's targetSdk, so the storage reflection tests have to run at the app's target, not
        // at whatever compileSdk is bumped to next.
        targetSdk = projectConfig.targetSdk

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
    implementation(project(":app-common"))
    implementation(project(":app-common-root"))
    implementation(project(":app-common-adb"))
    implementation(project(":app-common-shell"))

    addAndroidCore()
    addAndroidUI()
    addDI()
    addCoroutines()
    addSerialization()
    addIOApi()

    addTesting()
    testImplementation(project(":app-common-test"))
    testImplementation(project(":app-common-coil"))
    testImplementation("androidx.test.ext:junit:1.3.0")
}
