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
    namespace = "${projectConfig.packageName}.common.stats"

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

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:${Versions.Desugar.core}")
    implementation(project(":app-common"))
    implementation(project(":app-common-ui"))
    implementation(project(":app-common-io"))
    implementation(project(":app-common-coil"))
    implementation(project(":app-common-pkgs"))
    implementation(project(":app-common-data"))

    addAndroidCore()
    addAndroidUI()
    addCompose()
    addNavigation3()
    addDI()
    addCoroutines()
    addSerialization()
    addRoomDb()

    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation("androidx.preference:preference-ktx:1.2.1")

    addTesting()
    testImplementation(project(":app-common-test"))
}
