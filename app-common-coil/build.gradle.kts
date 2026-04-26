plugins {
    id("com.android.library")
    id("kotlin-parcelize")
    id("com.google.devtools.ksp")
    id("projectConfig")
    id("org.jetbrains.kotlin.plugin.compose")
}

apply(plugin = "dagger.hilt.android.plugin")
apply(plugin = "org.jetbrains.kotlin.plugin.serialization")

android {
    namespace = "${projectConfig.packageName}.common.coil"

    setupLibraryDefaults(projectConfig)

    setupModuleBuildTypes()

    buildFeatures {
        compose = true
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
    implementation(project(":app-common-ui"))
    implementation(project(":app-common-io"))
    implementation(project(":app-common-data"))
    implementation(project(":app-common-pkgs"))

    addAndroidCore()
    addAndroidUI()
    addCompose()
    addNavigation3()
    addDI()
    addCoroutines()
    addCoilApi()
    api("io.coil-kt:coil-compose:2.7.0")
    addSerialization()

    implementation("io.github.panpf.zoomimage:zoomimage-view-coil2:1.4.0")

    implementation("androidx.navigation:navigation-fragment-ktx:${Versions.AndroidX.Navigation.core}")
    implementation("androidx.navigation:navigation-ui-ktx:${Versions.AndroidX.Navigation.core}")

    addTesting()
    testImplementation(project(":app-common-test"))
}
