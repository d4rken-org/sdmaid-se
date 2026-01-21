import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File
import java.io.FileInputStream
import java.util.Properties

fun LibraryExtension.setupLibraryDefaults(projectConfig: ProjectConfig) {
    if (projectConfig.compileSdkPreview != null) {
        compileSdkPreview = projectConfig.compileSdkPreview
    } else {
        compileSdk = projectConfig.compileSdk
    }

    defaultConfig {
        minSdk = projectConfig.minSdk
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

val Project.projectConfig: ProjectConfig
    get() = extensions.findByType(ProjectConfig::class.java)!!

fun LibraryExtension.setupModuleBuildTypes() {
    buildTypes {
        debug {
            consumerProguardFiles("consumer-rules.pro")
        }
        create("beta") {
            consumerProguardFiles("consumer-rules.pro")
        }
        release {
            consumerProguardFiles("consumer-rules.pro")
        }
    }
}

fun Project.setupKotlinOptions() {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.addAll(
                listOf(
                    "-opt-in=kotlin.RequiresOptIn",
                    "-opt-in=kotlin.ExperimentalStdlibApi",
                    "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                    "-opt-in=kotlinx.coroutines.FlowPreview",
                    "-opt-in=kotlin.time.ExperimentalTime",
                    "-Xjvm-default=all",
                    "-Xcontext-parameters",
                    "-Xannotation-default-target=param-property",
                )
            )
        }
    }
}

fun LibraryExtension.setupCompileOptions() {
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

fun com.android.build.api.dsl.SigningConfig.setupCredentials(
    signingPropsPath: File? = null
) {

    val keyStoreFromEnv = System.getenv("STORE_PATH")?.let { File(it) }

    if (keyStoreFromEnv?.exists() == true) {
        println("Using signing data from environment variables.")
        storeFile = keyStoreFromEnv
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS")
        keyPassword = System.getenv("KEY_PASSWORD")
    } else {
        println("Using signing data from properties file.")
        val props = Properties().apply {
            signingPropsPath?.takeIf { it.canRead() }?.let { load(FileInputStream(it)) }
        }

        val keyStorePath = props.getProperty("release.storePath")?.let { File(it) }

        if (keyStorePath?.exists() == true) {
            storeFile = keyStorePath
            storePassword = props.getProperty("release.storePassword")
            keyAlias = props.getProperty("release.keyAlias")
            keyPassword = props.getProperty("release.keyPassword")
        }
    }
}

fun Test.setupTests() {
    maxHeapSize = "2048m"
    maxParallelForks = 2
    forkEvery = 100

    testLogging {
        events(
            TestLogEvent.FAILED,
            TestLogEvent.SKIPPED,
        )
        exceptionFormat = TestExceptionFormat.SHORT
        showExceptions = true
        showCauses = true
        showStackTraces = false
    }

    val failedTests = mutableListOf<String>()
    var totalTests = 0
    var passedTests = 0
    var failedTestCount = 0
    var skippedTests = 0

    addTestListener(object : TestListener {
        override fun beforeSuite(suite: TestDescriptor) {
            if (suite.parent == null) {
                println("\n🧪 Starting test suite: ${suite.displayName}")
            }
        }

        override fun beforeTest(testDescriptor: TestDescriptor) {}

        override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
            when (result.resultType) {
                TestResult.ResultType.SUCCESS -> {
                    print(".")
                    passedTests++
                }

                TestResult.ResultType.FAILURE -> {
                    print("F")
                    failedTests.add("${testDescriptor.className} > ${testDescriptor.displayName}")
                    failedTestCount++
                }

                TestResult.ResultType.SKIPPED -> {
                    print("S")
                    skippedTests++
                }
            }
            totalTests++
        }

        override fun afterSuite(suite: TestDescriptor, result: TestResult) {
            // Only show final summary for the root suite
            if (suite.parent == null) {
                println("\n")
                println("=".repeat(80))
                println("📊 TEST RESULTS SUMMARY")
                println("=".repeat(80))
                println("Total: ${result.testCount} | ✅ Passed: ${result.successfulTestCount} | ❌ Failed: ${result.failedTestCount} | ⏭️ Skipped: ${result.skippedTestCount}")
                println("Duration: ${(result.endTime - result.startTime) / 1000.0}s")

                if (failedTests.isNotEmpty()) {
                    println("\n❌ FAILED TESTS:")
                    println("-".repeat(80))
                    failedTests.forEach { testName ->
                        println("  • $testName")
                    }
                    println("-".repeat(80))
                }

                println("Result: ${if (result.resultType == TestResult.ResultType.SUCCESS) "✅ PASSED" else "❌ FAILED"}")
                println("=".repeat(80))
            }
        }
    })
}
