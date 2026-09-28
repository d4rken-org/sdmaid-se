plugins {
    id("com.android.test")
    id("projectConfig")
}

android {
    namespace = "${projectConfig.packageName}.e2e"
    compileSdk = projectConfig.compileSdk

    defaultConfig {
        minSdk = projectConfig.minSdk
        targetSdk = projectConfig.targetSdk
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Runs in its own process and drives the app from outside, so the app under test is the unmodified APK.
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    flavorDimensions.add("version")
    productFlavors {
        create("foss") { dimension = "version" }
        create("gplay") { dimension = "version" }
    }

    buildTypes {
        // Matches the app's R8-minified beta build.
        create("beta") {
            signingConfig = signingConfigs["debug"]
        }
    }

    // The flavor decides which onboarding switches exist.
    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents {
    beforeVariants { variant ->
        variant.enable = variant.buildType == "beta"
    }
}

// One emulator, one package: the :app debug tests and both beta variants must not overlap.
tasks.configureEach {
    when (name) {
        "connectedFossBetaAndroidTest" -> mustRunAfter(":app:connectedFossDebugAndroidTest")
        "connectedGplayBetaAndroidTest" -> mustRunAfter(
            ":app:connectedFossDebugAndroidTest",
            "connectedFossBetaAndroidTest",
        )
    }
}

setupKotlinOptions()

dependencies {
    implementation("androidx.test:runner:1.7.0")
    implementation("androidx.test.ext:junit:1.3.0")
    implementation("androidx.test.uiautomator:uiautomator:2.4.0")
}
