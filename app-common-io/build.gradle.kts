plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlin-parcelize")
}

apply(plugin = "dagger.hilt.android.plugin")

android {
    namespace = "${ProjectConfig.packageName}.common.io"
    compileSdk = ProjectConfig.compileSdk

    defaultConfig {
        minSdk = ProjectConfig.minSdk
        targetSdk = ProjectConfig.targetSdk

        consumerProguardFiles("consumer-rules.pro")
    }

    setupModuleBuildTypes()

    buildFeatures {
        viewBinding = true
        aidl = true
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
    implementation(project(":app-common-root"))
    implementation(project(":app-common-shizuku"))
    implementation(project(":app-common-shell"))

    addAndroidCore()
    addAndroidUI()
    addDI()
    addCoroutines()
    addSerialization()
    addIO()

    addTesting()
    testImplementation(project(":app-common-test"))
    testImplementation("org.robolectric:robolectric:4.9.1")
    testImplementation("androidx.test.ext:junit:1.1.4")
}