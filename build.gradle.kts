plugins {
    id("projectConfig")
    id("com.google.devtools.ksp") version "2.3.6" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:9.1.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.Kotlin.core}")
        classpath("com.google.dagger:hilt-android-gradle-plugin:${Versions.Dagger.core}")
        classpath("org.jetbrains.kotlin:kotlin-serialization:${Versions.Kotlin.core}")
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

tasks.register("testToolModules") {
    description = "Run unit tests for all app-tool-* library modules"
    dependsOn(subprojects.filter { it.name.startsWith("app-tool") }.map { ":${it.name}:testDebugUnitTest" })
}

tasks.register("testCommonModules") {
    description = "Run unit tests for all app-common-* library modules"
    dependsOn(subprojects.filter { it.name.startsWith("app-common") }.map { ":${it.name}:testDebugUnitTest" })
}