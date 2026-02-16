plugins {
    `kotlin-dsl`
    `java-library`
}

gradlePlugin {
    plugins {
        create("projectConfigPlugin") {
            id = "projectConfig"
            implementationClass = "ProjectConfigPlugin"
        }
    }
}

repositories {
    google()
    mavenCentral()
}
dependencies {
    implementation("com.android.tools.build:gradle:9.0.1")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10")
    implementation("com.squareup:javapoet:1.13.0")
}