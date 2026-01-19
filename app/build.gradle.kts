plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-parcelize")
    id("projectConfig")
    id("com.google.devtools.ksp")
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
    }

    signingConfigs {
        val basePath = File(System.getProperty("user.home"), ".appconfig/${projectConfig.packageName}")
        create("releaseFoss") {
            if (basePath.exists()) {
                setupCredentials(File(basePath, "signing-foss.properties"))
            } else {
                initWith(signingConfigs["debug"])
            }
        }
        create("releaseGplay") {
            if (basePath.exists()) {
                setupCredentials(File(basePath, "signing-gplay-upload.properties"))
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
        all {
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        create("beta") {
            lint {
                abortOnError = true
                fatal.add("StopShip")
            }
            isMinifyEnabled = true
            isShrinkResources = true
        }
        release {
            lint {
                abortOnError = true
                fatal.add("StopShip")
            }
            isMinifyEnabled = true
            isShrinkResources = true
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

    applicationVariants.all {
        val variantName = name
            .replace(Regex("([a-z])([A-Z])"), "$1-$2")
            .uppercase()
        if (listOf("release", "beta").any { variantName.lowercase().contains(it) }) {
            outputs.all {
                val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
                val outputFileName = projectConfig.packageName +
                        "-v${defaultConfig.versionName}-${defaultConfig.versionCode}" +
                        "-${variantName}.apk"
                output.outputFileName = outputFileName
            }
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

setupKotlinOptions()

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
    implementation(project(":app-common-adb"))
    implementation(project(":app-common-io"))
    implementation(project(":app-common-pkgs"))
    implementation(project(":app-common-shell"))

    addDI()
    addCoroutines()
    addSerialization()
    addIO()
    addRetrofit()

    "gplayImplementation"("com.android.billingclient:billing:8.0.0")
    "gplayImplementation"("com.android.billingclient:billing-ktx:8.0.0")

    //noinspection GradleDependency See https://issuetracker.google.com/issues/374691245
    "gplayImplementation"("com.google.android.play:review:2.0.1")
    //noinspection GradleDependency See https://issuetracker.google.com/issues/374691245
    "gplayImplementation"("com.google.android.play:review-ktx:2.0.1")

    addAndroidCore()
    addAndroidUI()
    addWorkerManager()

    addRoomDb()

    addTesting()

    implementation("io.github.z4kn4fein:semver:3.0.0")

    addCoil()
    addLottie()

    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.github.reddit:IndicatorFastScroll:f9576c7") // 1.4.0
    implementation("me.zhanghai.android.fastscroll:library:1.3.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.recyclerview:recyclerview-selection:1.2.0")

    implementation("androidx.core:core-splashscreen:1.0.0")

    implementation("androidx.navigation:navigation-fragment-ktx:${Versions.AndroidX.Navigation.core}")
    implementation("androidx.navigation:navigation-ui-ktx:${Versions.AndroidX.Navigation.core}")
    androidTestImplementation("androidx.navigation:navigation-testing:${Versions.AndroidX.Navigation.core}")




    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("androidx.test.ext:junit:1.3.0")
}