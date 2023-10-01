plugins {
    `kotlin-dsl`
    `java-library`
}

repositories {
    google()
    mavenCentral()
}
dependencies {
    implementation("com.android.tools.build:gradle:8.1.2")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.20")
    implementation("com.squareup:javapoet:1.13.0")
}