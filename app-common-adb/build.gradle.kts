plugins {
    id("com.android.library")
    id("kotlin-parcelize")
    id("com.google.devtools.ksp")
    id("projectConfig")
}

apply(plugin = "dagger.hilt.android.plugin")
apply(plugin = "org.jetbrains.kotlinx.kover")

android {
    namespace = "${projectConfig.packageName}.common.adb"

    setupLibraryDefaults(projectConfig)

    setupModuleBuildTypes()

    buildFeatures {
        aidl = true
    }

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
    implementation(project(":app-common"))

    addAndroidCore()
    addDI()
    addCoroutines()
    addSerialization()

    addTesting()
    testImplementation(project(":app-common-test"))

    implementation("com.github.d4rken-org.porter-api:sdk:0.7.0")
    // Lets the SDK receive binders from Shizuku servers too. Must not coexist with
    // dev.rikka.shizuku:provider - both ship moe.shizuku.api.BinderContainer.
    implementation("com.github.d4rken-org.porter-api:shizuku-compat:0.7.0")
}