import org.gradle.api.Action
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.kotlin.dsl.DependencyHandlerScope
import org.gradle.kotlin.dsl.accessors.runtime.addDependencyTo
import org.gradle.kotlin.dsl.exclude

private fun DependencyHandler.implementation(dependencyNotation: Any): Dependency? =
    add("implementation", dependencyNotation)

private fun DependencyHandler.testImplementation(dependencyNotation: Any): Dependency? =
    add("testImplementation", dependencyNotation)

private fun DependencyHandler.testImplementation(
    dependencyNotation: String,
    dependencyConfiguration: Action<ExternalModuleDependency>
): ExternalModuleDependency = addDependencyTo(
    this, "testImplementation", dependencyNotation, dependencyConfiguration
)

private fun DependencyHandler.ksp(dependencyNotation: Any): Dependency? =
    add("ksp", dependencyNotation)

private fun DependencyHandler.kspTest(dependencyNotation: Any): Dependency? =
    add("kspTest", dependencyNotation)

private fun DependencyHandler.androidTestImplementation(dependencyNotation: Any): Dependency? =
    add("androidTestImplementation", dependencyNotation)

private fun DependencyHandler.androidTestImplementation(
    dependencyNotation: String,
    dependencyConfiguration: Action<ExternalModuleDependency>
): ExternalModuleDependency = addDependencyTo(
    this, "androidTestImplementation", dependencyNotation, dependencyConfiguration
)

private fun DependencyHandler.kspAndroidTest(dependencyNotation: Any): Dependency? =
    add("kspAndroidTest", dependencyNotation)

private fun DependencyHandler.testRuntimeOnly(dependencyNotation: Any): Dependency? =
    add("testRuntimeOnly", dependencyNotation)

private fun DependencyHandler.debugImplementation(dependencyNotation: Any): Dependency? =
    add("debugImplementation", dependencyNotation)

private fun DependencyHandler.api(dependencyNotation: Any): Dependency? =
    add("api", dependencyNotation)

fun DependencyHandlerScope.addDI() {
    implementation("com.google.dagger:dagger:${Versions.Dagger.core}")
    implementation("com.google.dagger:dagger-android:${Versions.Dagger.core}")

    ksp("com.google.dagger:dagger-compiler:${Versions.Dagger.core}")
    kspTest("com.google.dagger:dagger-compiler:${Versions.Dagger.core}")

    ksp("com.google.dagger:dagger-android-processor:${Versions.Dagger.core}")
    kspTest("com.google.dagger:dagger-android-processor:${Versions.Dagger.core}")

    implementation("com.google.dagger:hilt-android:${Versions.Dagger.core}")
    ksp("com.google.dagger:hilt-android-compiler:${Versions.Dagger.core}")
    kspTest("com.google.dagger:hilt-android-compiler:${Versions.Dagger.core}")

    testImplementation("com.google.dagger:hilt-android-testing:${Versions.Dagger.core}")

    androidTestImplementation("com.google.dagger:hilt-android-testing:${Versions.Dagger.core}")
    kspAndroidTest("com.google.dagger:hilt-android-compiler:${Versions.Dagger.core}")
}

fun DependencyHandlerScope.addCoroutines() {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${Versions.Kotlin.core}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.Kotlin.coroutines}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.Kotlin.coroutines}")

    testImplementation("org.jetbrains.kotlin:kotlin-reflect:${Versions.Kotlin.core}")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.Kotlin.coroutines}") {
        // 2 files found with path 'win32-x86-64/attach_hotspot_windows.dll'
        exclude("org.jetbrains.kotlinx", "kotlinx-coroutines-debug")
    }
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.Kotlin.coroutines}") {
        // 2 files found with path 'win32-x86-64/attach_hotspot_windows.dll'
        exclude("org.jetbrains.kotlinx", "kotlinx-coroutines-debug")
    }
}

private const val COIL_VERSION = "2.7.0"

fun DependencyHandlerScope.addCoil() {
    implementation("io.coil-kt:coil:$COIL_VERSION")
    implementation("io.coil-kt:coil-video:$COIL_VERSION")
}

fun DependencyHandlerScope.addCoilApi() {
    api("io.coil-kt:coil:$COIL_VERSION")
    implementation("io.coil-kt:coil-video:$COIL_VERSION")
}

fun DependencyHandlerScope.addLottie() {
    implementation("com.airbnb.android:lottie:6.7.1")
    implementation("com.airbnb.android:lottie-compose:6.7.1")
}

fun DependencyHandlerScope.addSerialization() {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.Kotlin.serialization}")
}

private const val OKIO_VERSION = "3.16.4"

fun DependencyHandlerScope.addIO() {
    implementation("com.squareup.okio:okio:$OKIO_VERSION")
}

fun DependencyHandlerScope.addIOApi() {
    api("com.squareup.okio:okio:$OKIO_VERSION")
}

fun DependencyHandlerScope.addRetrofit() {
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:3.0.0")
    implementation("com.squareup.retrofit2:converter-scalars:3.0.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

fun DependencyHandlerScope.addAndroidCore() {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.annotation:annotation:1.9.1")

    implementation("androidx.datastore:datastore-preferences:1.2.0")
}

fun DependencyHandlerScope.addRoomDb() {
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
}

fun DependencyHandlerScope.addWorkerManager() {
    val version = "2.11.0"
    implementation("androidx.work:work-runtime:$version")
    testImplementation("androidx.work:work-testing:$version")
    implementation("androidx.work:work-runtime-ktx:$version")

    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")
}

fun DependencyHandlerScope.addAndroidUI() {
    implementation("androidx.activity:activity-ktx:1.12.4")

    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.9.4")
    implementation("androidx.lifecycle:lifecycle-common-java8:2.9.4")
    implementation("androidx.lifecycle:lifecycle-process:2.9.4")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.9.4")

    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("com.google.android.material:material:1.13.0")
}

fun DependencyHandlerScope.addCompose() {
    val composeBom = platform("androidx.compose:compose-bom:${Versions.AndroidX.Compose.bom}")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    testImplementation(composeBom)

    // Override foundation to get Modifier.visible()
    implementation("androidx.compose.foundation:foundation:${Versions.AndroidX.Compose.foundationOverride}")

    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.compose.ui:ui-test-android")
    testImplementation("androidx.compose.ui:ui-test-junit4-android")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3.adaptive:adaptive")

    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")

    implementation("androidx.hilt:hilt-navigation-compose:1.3.0-alpha01")

    implementation("com.google.accompanist:accompanist-drawablepainter:0.37.3")
}

fun DependencyHandlerScope.addNavigation3() {
    implementation("androidx.navigation3:navigation3-runtime-android:${Versions.AndroidX.Navigation3.core}")
    implementation("androidx.navigation3:navigation3-ui-android:${Versions.AndroidX.Navigation3.core}")

    implementation("androidx.lifecycle:lifecycle-viewmodel-navigation3-android:${Versions.AndroidX.Navigation3.lifecycleVm}")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:${Versions.Kotlin.serialization}")
}

fun DependencyHandlerScope.addTesting() {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit.vintage:junit-vintage-engine:5.14.2")
    testImplementation("androidx.test:core-ktx:1.7.0")

    testImplementation("io.mockk:mockk:1.14.9")
    androidTestImplementation("io.mockk:mockk-android:1.14.9")

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.14.2")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.14.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.14.2")


    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core-jvm:5.9.1")
    testImplementation("io.kotest:kotest-property-jvm:5.9.1")
    androidTestImplementation("io.kotest:kotest-assertions-core-jvm:5.9.1")
    androidTestImplementation("io.kotest:kotest-property-jvm:5.9.1")

    testImplementation("androidx.arch.core:core-testing:2.2.0")
    androidTestImplementation("androidx.arch.core:core-testing:2.2.0")
    debugImplementation("androidx.test:core-ktx:1.7.0")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")

    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-contrib:3.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.7.0")
    androidTestImplementation("androidx.test.espresso.idling:idling-concurrent:3.7.0")
}
