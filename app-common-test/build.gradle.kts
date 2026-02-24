plugins {
    id("com.android.library")
    id("com.google.devtools.ksp")
    id("projectConfig")
}

android {
    namespace = "${projectConfig.packageName}.common.test"

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
    addIO()
    addSerialization()

    implementation("junit:junit:4.13.2")
    implementation("org.junit.vintage:junit-vintage-engine:5.14.2")
    implementation("androidx.test:core-ktx:1.7.0")

    implementation("io.mockk:mockk:1.14.9")

    runtimeOnly("org.junit.jupiter:junit-jupiter-engine:5.14.2")
    implementation("org.junit.jupiter:junit-jupiter-api:5.14.2")
    implementation("org.junit.jupiter:junit-jupiter-params:5.14.2")


    implementation("io.kotest:kotest-runner-junit5:5.9.1")
    implementation("io.kotest:kotest-assertions-core-jvm:5.9.1")
    implementation("io.kotest:kotest-property-jvm:5.9.1")
}