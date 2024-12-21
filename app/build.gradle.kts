plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-parcelize")
    id("projectConfig")
    id("com.google.devtools.ksp")
    id("kotlin-kapt")
}
apply(plugin = "dagger.hilt.android.plugin")
apply(plugin = "androidx.navigation.safeargs.kotlin")

val commitHashProvider = providers.of(CommitHashValueSource::class) {}

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

        javaCompileOptions {
            annotationProcessorOptions {
                arguments(
                    mapOf("room.schemaLocation" to "$projectDir/schemas")
                )
            }
        }
    }

    signingConfigs {
        val basePath = File(System.getProperty("user.home"), ".appconfig/${projectConfig.packageName}")
        create("releaseFoss") {
            setupCredentials(File(basePath, "signing-foss.properties"))
        }
        create("releaseGplay") {
            setupCredentials(File(basePath, "signing-gplay-upload.properties"))
        }
    }

    flavorDimensions.add("version")
    productFlavors {
        create("foss") {
            dimension = "version"
            signingConfig = signingConfigs["releaseFoss"]
            // The info block is encrypted and can only be read by google
            dependenciesInfo {
                includeInApk = false
                includeInBundle = false
            }
        }
        create("gplay") {
            dimension = "version"
            signingConfig = signingConfigs["releaseGplay"]
        }
    }

    buildTypes {
        val customProguardRules = fileTree(File(projectDir, "proguard")) {
            include("*.pro")
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            proguardFiles(*customProguardRules.toList().toTypedArray())
            proguardFiles("proguard-rules-debug.pro")
        }
        create("beta") {
            lint {
                abortOnError = true
                fatal.add("StopShip")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            proguardFiles(*customProguardRules.toList().toTypedArray())
        }
        release {
            lint {
                abortOnError = true
                fatal.add("StopShip")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            proguardFiles(*customProguardRules.toList().toTypedArray())
        }
    }

    buildOutputs.all {
        val variantOutputImpl = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
        val variantName: String = variantOutputImpl.name

        if (listOf("release", "beta").any { variantName.lowercase().contains(it) }) {
            val outputFileName = projectConfig.packageName +
                    "-v${defaultConfig.versionName}-${defaultConfig.versionCode}" +
                    "-${variantName.uppercase()}.apk"

            variantOutputImpl.outputFileName = outputFileName
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

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

    sourceSets {
        getByName("test") {
            resources.srcDirs("src/main/assets")
        }
        getByName("androidTest") {
            assets.srcDirs(files("$projectDir/schemas"))
        }
    }

    androidResources {
        generateLocaleConfig = true
    }
}

afterEvaluate {
    tasks {
        named("bundleGplayBeta") {
            dependsOn("lintVitalGplayBeta")
        }
        named("bundleGplayRelease") {
            dependsOn("lintVitalGplayRelease")
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:${Versions.Desugar.core}")

    implementation(project(":app-common"))
    testImplementation(project(":app-common-test"))
    implementation(project(":app-common-root"))
    implementation(project(":app-common-shizuku"))
    implementation(project(":app-common-io"))
    implementation(project(":app-common-pkgs"))

    addDI()
    addCoroutines()
    addSerialization()
    addIO()
    addRetrofit()

    "gplayImplementation"("com.android.billingclient:billing:6.2.1")
    "gplayImplementation"("com.android.billingclient:billing-ktx:6.2.1")

    "gplayImplementation"("com.google.android.play:review:2.0.1")
    "gplayImplementation"("com.google.android.play:review-ktx:2.0.1")

    addAndroidCore()
    addAndroidUI()
    addWorkerManager()

    addRoomDb()

    addTesting()

    implementation("io.github.z4kn4fein:semver:1.4.2")

    addCoil()
    addLottie()

    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.github.reddit:IndicatorFastScroll:f9576c7") // 1.4.0
    implementation("me.zhanghai.android.fastscroll:library:1.2.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.recyclerview:recyclerview-selection:1.1.0")

    implementation("androidx.core:core-splashscreen:1.0.0")

    implementation("androidx.navigation:navigation-fragment-ktx:${Versions.AndroidX.Navigation.core}")
    implementation("androidx.navigation:navigation-ui-ktx:${Versions.AndroidX.Navigation.core}")
    androidTestImplementation("androidx.navigation:navigation-testing:${Versions.AndroidX.Navigation.core}")




    testImplementation("org.robolectric:robolectric:4.9.1")
    testImplementation("androidx.test.ext:junit:1.1.4")
}