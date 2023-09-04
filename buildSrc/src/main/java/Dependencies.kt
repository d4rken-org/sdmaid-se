import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.kotlin.dsl.DependencyHandlerScope

private fun DependencyHandler.implementation(dependencyNotation: Any): Dependency? =
    add("implementation", dependencyNotation)

private fun DependencyHandler.testImplementation(dependencyNotation: Any): Dependency? =
    add("testImplementation", dependencyNotation)

private fun DependencyHandler.kapt(dependencyNotation: Any): Dependency? =
    add("kapt", dependencyNotation)

private fun DependencyHandler.kaptTest(dependencyNotation: Any): Dependency? =
    add("kaptTest", dependencyNotation)

private fun DependencyHandler.androidTestImplementation(dependencyNotation: Any): Dependency? =
    add("androidTestImplementation", dependencyNotation)

private fun DependencyHandler.kaptAndroidTest(dependencyNotation: Any): Dependency? =
    add("kaptAndroidTest", dependencyNotation)

private fun DependencyHandler.`testRuntimeOnly`(dependencyNotation: Any): Dependency? =
    add("testRuntimeOnly", dependencyNotation)

private fun DependencyHandler.`debugImplementation`(dependencyNotation: Any): Dependency? =
    add("debugImplementation", dependencyNotation)

fun DependencyHandlerScope.addDI() {
    implementation("com.google.dagger:dagger:${Versions.Dagger.core}")
    implementation("com.google.dagger:dagger-android:${Versions.Dagger.core}")

    kapt("com.google.dagger:dagger-compiler:${Versions.Dagger.core}")
    kaptTest("com.google.dagger:dagger-compiler:${Versions.Dagger.core}")

    kapt("com.google.dagger:dagger-android-processor:${Versions.Dagger.core}")
    kaptTest("com.google.dagger:dagger-android-processor:${Versions.Dagger.core}")

    implementation("com.google.dagger:hilt-android:${Versions.Dagger.core}")
    kapt("com.google.dagger:hilt-android-compiler:${Versions.Dagger.core}")
    kaptTest("com.google.dagger:hilt-android-compiler:${Versions.Dagger.core}")

    testImplementation("com.google.dagger:hilt-android-testing:${Versions.Dagger.core}")

    androidTestImplementation("com.google.dagger:hilt-android-testing:${Versions.Dagger.core}")
    kaptAndroidTest("com.google.dagger:hilt-android-compiler:${Versions.Dagger.core}")
}

fun DependencyHandlerScope.addCoroutines() {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${Versions.Kotlin.core}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.Kotlin.coroutines}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.Kotlin.coroutines}")
    implementation("org.jetbrains.kotlin:kotlin-reflect:${Versions.Kotlin.core}")

    testImplementation("org.jetbrains.kotlin:kotlin-reflect:${Versions.Kotlin.core}")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.Kotlin.coroutines}")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.Kotlin.coroutines}")
//    {
//        // conflicts with mockito due to direct inclusion of byte buddy
//        exclude("org.jetbrains.kotlinx", "kotlinx-coroutines-debug")
//    }
}

fun DependencyHandlerScope.addSerialization() {
    implementation("com.squareup.moshi:moshi:${Versions.Moshi.core}")
    implementation("com.squareup.moshi:moshi-adapters:${Versions.Moshi.core}")
    kapt("com.squareup.moshi:moshi-kotlin-codegen:${Versions.Moshi.core}")
}

fun DependencyHandlerScope.addIO() {
    implementation("com.squareup.okio:okio:3.1.0")
}

fun DependencyHandlerScope.addRetrofit() {
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.9.0")
    implementation("com.squareup.retrofit2:converter-scalars:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.1")

    testImplementation("com.squareup.okhttp3:mockwebserver:4.11.0")
}

fun DependencyHandlerScope.addAndroidCore() {
    implementation("androidx.core:core-ktx:1.8.0")
    implementation("androidx.appcompat:appcompat:1.4.2")
    implementation("androidx.annotation:annotation:1.4.0")

    implementation("androidx.preference:preference-ktx:1.2.0")
    implementation("androidx.datastore:datastore-preferences:1.0.0")
}

fun DependencyHandlerScope.addWorkerManager() {
    implementation("androidx.work:work-runtime:${Versions.AndroidX.WorkManager.core}")
    testImplementation("androidx.work:work-testing:${Versions.AndroidX.WorkManager.core}")
    implementation("androidx.work:work-runtime-ktx:${Versions.AndroidX.WorkManager.core}")

    implementation("androidx.hilt:hilt-work:1.0.0")
    kapt("androidx.hilt:hilt-compiler:1.0.0")
}

fun DependencyHandlerScope.addAndroidUI() {
    implementation("androidx.activity:activity-ktx:1.6.1")
    implementation("androidx.fragment:fragment-ktx:1.5.5")

    implementation("androidx.lifecycle:lifecycle-extensions:2.2.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.5.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.5.1")
    implementation("androidx.lifecycle:lifecycle-common-java8:2.5.1")
    implementation("androidx.lifecycle:lifecycle-process:2.5.1")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.5.1")

    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.material:material:1.7.0")
}

fun DependencyHandlerScope.addTesting() {
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