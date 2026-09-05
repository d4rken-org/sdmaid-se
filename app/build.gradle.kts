plugins {
    id("com.android.application")
    id("kotlin-parcelize")
    id("projectConfig")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.android.compose.screenshot")
}
apply(plugin = "dagger.hilt.android.plugin")
apply(plugin = "org.jetbrains.kotlin.plugin.serialization")

val commitHashProvider = providers.of(CommitHashValueSource::class) {}
val signingBasePath = File(System.getProperty("user.home"), ".config/projects/${projectConfig.packageName}")

android {
    if (projectConfig.compileSdkPreview != null) {
        compileSdkPreview = projectConfig.compileSdkPreview
    } else {
        compileSdk = projectConfig.compileSdk
    }

    defaultConfig {
        namespace = projectConfig.packageName

        minSdk = projectConfig.minSdk
        if (projectConfig.targetSdkPreview != null) {
            targetSdkPreview = projectConfig.targetSdkPreview
        } else {
            targetSdk = projectConfig.targetSdk
        }

        versionCode = projectConfig.version.code.toInt()
        versionName = projectConfig.version.name

        testInstrumentationRunner = "eu.darken.sdmse.HiltTestRunner"

        buildConfigField("String", "PACKAGENAME", "\"${projectConfig.packageName}\"")
        buildConfigField("String", "GITSHA", "\"${commitHashProvider.get()}\"")
        buildConfigField("String", "VERSION_CODE", "\"${projectConfig.version.code}\"")
        buildConfigField("String", "VERSION_NAME", "\"${projectConfig.version.name}\"")
    }

    signingConfigs {
        val hasEnvCredentials = System.getenv("STORE_PATH")?.let { File(it).exists() } == true
        create("releaseFoss") {
            if (hasEnvCredentials || signingBasePath.exists()) {
                setupCredentials(File(signingBasePath, "signing-foss.properties"))
            } else {
                initWith(signingConfigs["debug"])
            }
        }
        create("releaseGplay") {
            if (hasEnvCredentials || signingBasePath.exists()) {
                setupCredentials(File(signingBasePath, "signing-gplay-upload.properties"))
            } else {
                initWith(signingConfigs["debug"])
            }
        }
    }

    flavorDimensions.add("version")
    productFlavors {
        create("foss") {
            dimension = "version"
            signingConfig = signingConfigs["releaseFoss"]
            proguardFiles("proguard-foss.pro")
            // The info block is encrypted and can only be read by Google
            dependenciesInfo {
                includeInApk = false
                includeInBundle = false
            }
        }
        create("gplay") {
            dimension = "version"
            signingConfig = signingConfigs["releaseGplay"]
            proguardFiles("proguard-gplay.pro")
        }
    }

    buildTypes {
        all {
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        create("beta") {
            isMinifyEnabled = true
            isShrinkResources = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
        }
    }

    lint {
        abortOnError = true
        applySdmSeDefaults()
        fatal.add("StopShip")
        // AGP has no per-buildType lint config; beta builds tolerate translations ahead of source strings
        val isBetaBuild = gradle.startParameter.taskNames.any { it.contains("beta", ignoreCase = true) }
        if (isBetaBuild) warning.add("ExtraTranslation") else fatal.add("ExtraTranslation")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    // Enables the screenshotTest source set (com.android.compose.screenshot). The matching
    // apply-time gate lives in gradle.properties (android.experimental.enableScreenshotTest).
    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
        //noinspection WrongGradleMethod
        tasks.withType<Test> {
            useJUnitPlatform()
            setupTests()
        }
    }

    sourceSets {
        getByName("test") {
            resources.directories.add("src/main/assets")
        }
        getByName("androidTest") {
            assets.directories.add("$projectDir/schemas")
        }
    }

    androidResources {
        @Suppress("UnstableApiUsage")
        generateLocaleConfig = true
    }

    packaging {
        resources {
            excludes.add("attach_hotspot_windows.dll")
        }
    }

}

androidComponents {
    onVariants { variant ->
        val buildType = variant.buildType ?: return@onVariants
        if (buildType != "release" && buildType != "beta") return@onVariants

        val formattedVariantName = variant.name
            .replace(Regex("([a-z])([A-Z])"), "$1-$2")
            .uppercase()
        val isGplay = variant.flavorName == "gplay"
        // The gplay variant APK carries the upload key, not the Play app signing key; mark it
        // so it can't be confused with the installable re-signed APK produced below.
        val suffix = if (isGplay) "-UPLOAD" else ""

        val apkFileName = "${projectConfig.packageName}" +
            "-v${projectConfig.version.name}-${projectConfig.version.code}" +
            "-$formattedVariantName$suffix.apk"
        variant.outputs.single().outputFileName.set(apkFileName)

        // The gplay variant is signed with the upload key so the AAB passes Play's upload check.
        // For a directly installable Play-signed APK, re-sign the assembled APK with the app
        // signing key. Local-only: without signing-gplay.properties (e.g. CI) the task no-ops.
        if (isGplay) {
            tasks.register<SignGplayApkTask>("signGplay${buildType.replaceFirstChar { it.uppercase() }}Apk") {
                apkDir.set(variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.APK))
                apkName.set(apkFileName)
                signingProps.from(File(signingBasePath, "signing-gplay.properties"))
                sdkDir.set(sdkComponents.sdkDirectory)
                outputDir.set(layout.buildDirectory.dir("outputs/apk_gplay_signed/$buildType"))
            }
        }
    }
}

setupKotlinOptions(compose = true)

afterEvaluate {
    tasks.matching { it.name == "bundleGplayBeta" }.configureEach {
        dependsOn("lintVitalGplayBeta")
    }
    tasks.matching { it.name == "bundleGplayRelease" }.configureEach {
        dependsOn("lintVitalGplayRelease")
    }
    tasks.matching { it.name == "assembleGplayBeta" }.configureEach {
        finalizedBy("signGplayBetaApk")
    }
    tasks.matching { it.name == "assembleGplayRelease" }.configureEach {
        finalizedBy("signGplayReleaseApk")
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:${Versions.Desugar.core}")

    implementation(project(":app-common"))
    implementation(project(":app-common-ui"))
    implementation(project(":app-common-coil"))
    implementation(project(":app-common-picker"))
    implementation(project(":app-common-stats"))
    testImplementation(project(":app-common-test"))
    implementation(project(":app-common-root"))
    implementation(project(":app-common-adb"))
    implementation(project(":app-common-io"))
    implementation(project(":app-common-pkgs"))
    implementation(project(":app-common-shell"))
    implementation(project(":app-common-data"))
    implementation(project(":app-common-exclusion"))
    implementation(project(":app-common-automation"))
    implementation(project(":app-common-setup"))
    implementation(project(":app-tool-corpsefinder"))
    implementation(project(":app-tool-systemcleaner"))
    implementation(project(":app-tool-appcleaner"))
    implementation(project(":app-tool-deduplicator"))
    implementation(project(":app-tool-squeezer"))
    implementation(project(":app-tool-analyzer"))
    implementation(project(":app-tool-swiper"))
    implementation(project(":app-tool-appcontrol"))
    implementation(project(":app-tool-scheduler"))

    addDI()
    addCoroutines()
    addSerialization()
    addRetrofit()

    "gplayImplementation"("com.android.billingclient:billing:8.3.0")
    "gplayImplementation"("com.android.billingclient:billing-ktx:8.3.0")

    "gplayImplementation"("com.google.android.play:review:2.0.2")
    "gplayImplementation"("com.google.android.play:review-ktx:2.0.2")

    addAndroidCore()
    implementation("androidx.documentfile:documentfile:1.1.0")
    addAndroidUI()
    addCompose()
    addGlance()
    addNavigation3()
    addWorkerManager()
    // WorkManager exposes ListenableFuture in its API (TaskWorkerControl.kt).
    // Media3 Transformer (via app-tool-squeezer) transitively pulls in full Guava at runtime,
    // but `implementation` scoping means it doesn't reach this module's compile classpath.
    implementation("com.google.guava:guava:33.3.1-android")
    addRoomDb()

    addTesting()

    implementation("io.github.z4kn4fein:semver:3.0.0")

    addLottie()

    implementation("sh.calvin.reorderable:reorderable:2.5.1")

    implementation("androidx.exifinterface:exifinterface:1.4.2")

    androidTestImplementation("androidx.navigation:navigation-testing:${Versions.AndroidX.Navigation.core}")




    testImplementation("androidx.test.ext:junit:1.3.0")

    addScreenshotTest()
}
