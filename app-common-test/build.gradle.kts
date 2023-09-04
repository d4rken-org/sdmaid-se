plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("kotlin-android")
    id("kotlin-kapt")
}

android {
    namespace = "eu.darken.sdmse.common"
    compileSdk = ProjectConfig.compileSdk

    defaultConfig {
        minSdk = ProjectConfig.minSdk
        targetSdk = ProjectConfig.targetSdk
    }

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
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.3")
    implementation(project(":app-common"))

    addAndroidCore()
    addIO()
    addSerialization()

    implementation("junit:junit:4.13.2")
    implementation("org.junit.vintage:junit-vintage-engine:5.10.0")
    implementation("androidx.test:core-ktx:1.5.0")

    implementation("io.mockk:mockk:1.13.7")

    runtimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
    implementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    implementation("org.junit.jupiter:junit-jupiter-params:5.10.0")


    implementation("io.kotest:kotest-runner-junit5:5.7.1")
    implementation("io.kotest:kotest-assertions-core-jvm:5.7.1")
    implementation("io.kotest:kotest-property-jvm:5.7.1")
}