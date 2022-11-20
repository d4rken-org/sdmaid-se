plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlin-parcelize")
}
apply(plugin = "dagger.hilt.android.plugin")
apply(plugin = "androidx.navigation.safeargs.kotlin")
apply(plugin = "com.bugsnag.android.gradle")

android {
    compileSdk = ProjectConfig.compileSdk

    defaultConfig {
        applicationId = ProjectConfig.packageName

        minSdk = ProjectConfig.minSdk
        targetSdk = ProjectConfig.targetSdk

        versionCode = ProjectConfig.Version.code
        versionName = ProjectConfig.Version.name

        testInstrumentationRunner = "eu.darken.sdmse.HiltTestRunner"

        buildConfigField("String", "GITSHA", "\"${lastCommitHash()}\"")
        buildConfigField("String", "BUILDTIME", "\"${buildTime()}\"")

        manifestPlaceholders["bugsnagApiKey"] = getBugSnagApiKey(
            File(System.getProperty("user.home"), ".appconfig/${ProjectConfig.packageName}/bugsnag.properties")
        ) ?: "fake"
    }

    signingConfigs {
        val basePath = File(System.getProperty("user.home"), ".appconfig/${ProjectConfig.packageName}")
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

        if (listOf("release", "beta").any { variantName.toLowerCase().contains(it) }) {
            val outputFileName = ProjectConfig.packageName +
                    "-v${defaultConfig.versionName}-${defaultConfig.versionCode}" +
                    "-${variantName.toUpperCase()}-${lastCommitHash()}.apk"

            variantOutputImpl.outputFileName = outputFileName
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-Xopt-in=kotlin.ExperimentalStdlibApi",
            "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-Xopt-in=kotlinx.coroutines.FlowPreview",
            "-Xopt-in=kotlin.time.ExperimentalTime",
            "-Xopt-in=kotlin.RequiresOptIn"
        )
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
        tasks.withType<Test> {
            useJUnitPlatform()
        }
    }

    sourceSets {
        getByName("test") {
            java.srcDir("$projectDir/src/testShared/java")
        }
        getByName("androidTest") {
            java.srcDir("$projectDir/src/testShared/java")
            assets.srcDirs(files("$projectDir/schemas"))
        }
    }
}

dependencies {
    // https://developer.android.com/studio/write/java8-support
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:1.1.5")

    implementation("org.jetbrains.kotlin:kotlin-stdlib:${Versions.Kotlin.core}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.Kotlin.coroutines}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.Kotlin.coroutines}")

    testImplementation("org.jetbrains.kotlin:kotlin-reflect:${Versions.Kotlin.core}")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.Kotlin.coroutines}")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.Kotlin.coroutines}") {
        // conflicts with mockito due to direct inclusion of byte buddy
        exclude("org.jetbrains.kotlinx", "kotlinx-coroutines-debug")
    }

    implementation("com.google.dagger:dagger:${Versions.Dagger.core}")
    implementation("com.google.dagger:dagger-android:${Versions.Dagger.core}")

    kapt("com.google.dagger:dagger-compiler:${Versions.Dagger.core}")
    kapt("com.google.dagger:dagger-android-processor:${Versions.Dagger.core}")

    implementation("com.google.dagger:hilt-android:${Versions.Dagger.core}")
    kapt("com.google.dagger:hilt-android-compiler:${Versions.Dagger.core}")

    testImplementation("com.google.dagger:hilt-android-testing:${Versions.Dagger.core}")
    kaptTest("com.google.dagger:hilt-android-compiler:${Versions.Dagger.core}")

    androidTestImplementation("com.google.dagger:hilt-android-testing:${Versions.Dagger.core}")
    kaptAndroidTest("com.google.dagger:hilt-android-compiler:${Versions.Dagger.core}")

    implementation("com.squareup.moshi:moshi:${Versions.Moshi.core}")
    implementation("com.squareup.moshi:moshi-adapters:${Versions.Moshi.core}")
    kapt("com.squareup.moshi:moshi-kotlin-codegen:${Versions.Moshi.core}")

    implementation("com.squareup.okio:okio:3.1.0")

    implementation("io.coil-kt:coil:2.0.0-rc02")

    // Debugging
    implementation("com.bugsnag:bugsnag-android:5.9.2")
    implementation("com.getkeepsafe.relinker:relinker:1.4.3")

    // Support libs
    implementation("androidx.core:core-ktx:1.8.0")
    implementation("androidx.appcompat:appcompat:1.4.2")
    implementation("androidx.annotation:annotation:1.4.0")

    implementation("androidx.activity:activity-ktx:1.5.0")
    implementation("androidx.fragment:fragment-ktx:1.5.0")

    implementation("androidx.lifecycle:lifecycle-extensions:2.2.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.5.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.5.0")
    implementation("androidx.lifecycle:lifecycle-common-java8:2.5.0")
    implementation("androidx.lifecycle:lifecycle-process:2.5.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.5.0")

    implementation("androidx.navigation:navigation-fragment-ktx:2.5.0")
    implementation("androidx.navigation:navigation-ui-ktx:2.5.0")
    androidTestImplementation("androidx.navigation:navigation-testing:2.5.0")

    implementation("androidx.preference:preference-ktx:1.2.0")

    implementation("androidx.datastore:datastore-preferences:1.0.0")

    implementation("androidx.core:core-splashscreen:1.0.0-alpha02")

    implementation("androidx.work:work-runtime:${Versions.AndroidX.WorkManager.core}")
    testImplementation("androidx.work:work-testing:${Versions.AndroidX.WorkManager.core}")

    // UI
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.material:material:1.5.0-rc01")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit.vintage:junit-vintage-engine:5.8.2")
    testImplementation("androidx.test:core-ktx:1.4.0")

    testImplementation("io.mockk:mockk:1.12.4")
    androidTestImplementation("io.mockk:mockk-android:1.12.4")

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.2")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.8.2")


    testImplementation("io.kotest:kotest-runner-junit5:5.3.0")
    testImplementation("io.kotest:kotest-assertions-core-jvm:5.3.0")
    testImplementation("io.kotest:kotest-property-jvm:5.3.0")
    androidTestImplementation("io.kotest:kotest-assertions-core-jvm:5.3.0")
    androidTestImplementation("io.kotest:kotest-property-jvm:5.3.0")

    testImplementation("android.arch.core:core-testing:1.1.1")
    androidTestImplementation("android.arch.core:core-testing:1.1.1")
    debugImplementation("androidx.test:core-ktx:1.4.0")

    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")

    androidTestImplementation("androidx.test:runner:1.4.0")
    androidTestImplementation("androidx.test:rules:1.4.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
    androidTestImplementation("androidx.test.espresso:espresso-contrib:3.4.0")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.4.0")
    androidTestImplementation("androidx.test.espresso.idling:idling-concurrent:3.4.0")
}