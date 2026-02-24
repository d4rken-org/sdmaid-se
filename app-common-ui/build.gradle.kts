plugins {
    id("com.android.library")
    id("kotlin-parcelize")
    id("com.google.devtools.ksp")
    id("projectConfig")
}

apply(plugin = "dagger.hilt.android.plugin")

android {
    namespace = "${projectConfig.packageName}.common.ui"

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

    addAndroidCore()
    addAndroidUI()
    implementation("androidx.navigation:navigation-fragment-ktx:${Versions.AndroidX.Navigation.core}")
    implementation("androidx.navigation:navigation-ui-ktx:${Versions.AndroidX.Navigation.core}")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.recyclerview:recyclerview-selection:1.2.0")
    implementation("me.zhanghai.android.fastscroll:library:1.3.0")
    addDI()
    addCoroutines()
    addSerialization()

    addCoil()
    addLottie()

    addTesting()
    testImplementation(project(":app-common-test"))
}
