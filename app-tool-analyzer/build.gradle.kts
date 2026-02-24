plugins {
    id("com.android.library")
    id("kotlin-parcelize")
    id("com.google.devtools.ksp")
    id("projectConfig")
}

apply(plugin = "dagger.hilt.android.plugin")

android {
    namespace = "${projectConfig.packageName}.analyzer"

    setupLibraryDefaults(projectConfig)

    setupModuleBuildTypes()

    buildFeatures {
        viewBinding = true
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
    implementation(project(":app-common-io"))
    implementation(project(":app-common-pkgs"))
    implementation(project(":app-common-root"))
    implementation(project(":app-common-shell"))
    implementation(project(":app-common-data"))
    implementation(project(":app-common-exclusion"))
    implementation(project(":app-common-setup"))

    implementation(project(":app-tool-systemcleaner"))
    implementation(project(":app-tool-swiper"))

    addAndroidCore()
    addAndroidUI()
    addDI()
    addCoroutines()
    addSerialization()
    addIO()
    addCoil()

    implementation("androidx.navigation:navigation-fragment-ktx:${Versions.AndroidX.Navigation.core}")
    implementation("androidx.navigation:navigation-ui-ktx:${Versions.AndroidX.Navigation.core}")
    implementation("androidx.recyclerview:recyclerview-selection:1.2.0")

    addTesting()
    testImplementation(project(":app-common-test"))
}
