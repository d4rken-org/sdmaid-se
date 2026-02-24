plugins {
    id("com.android.library")
    id("kotlin-parcelize")
    id("com.google.devtools.ksp")
    id("projectConfig")
}

apply(plugin = "dagger.hilt.android.plugin")

android {
    namespace = "${projectConfig.packageName}.common.io"

    setupLibraryDefaults(projectConfig)

    setupModuleBuildTypes()

    buildFeatures {
        viewBinding = true
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
    implementation(project(":app-common-root"))
    implementation(project(":app-common-adb"))
    implementation(project(":app-common-shell"))

    addAndroidCore()
    implementation("androidx.documentfile:documentfile:1.1.0")
    addAndroidUI()
    addDI()
    addCoroutines()
    addSerialization()
    addIO()

    addTesting()
    testImplementation(project(":app-common-test"))
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("androidx.test.ext:junit:1.3.0")
}