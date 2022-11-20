buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:${Versions.Gradle.buildTools}")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.Kotlin.core}")
        classpath("com.google.dagger:hilt-android-gradle-plugin:${Versions.Dagger.core}")
        classpath("androidx.navigation:navigation-safe-args-gradle-plugin:${Versions.AndroidX.Navigation.core}")
        classpath("com.bugsnag:bugsnag-android-gradle-plugin:7.2.1")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
    }
}

tasks.register("clean").configure {
    delete("build")
}