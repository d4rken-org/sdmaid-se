import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import org.gradle.api.Action
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions
import java.io.File
import java.io.FileInputStream
import java.util.Properties

/**
 * Configures the [kotlinOptions][org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions] extension.
 */
private fun LibraryExtension.kotlinOptions(configure: Action<KotlinJvmOptions>): Unit =
    (this as org.gradle.api.plugins.ExtensionAware).extensions.configure("kotlinOptions", configure)

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

fun com.android.build.api.dsl.CommonExtension<
        com.android.build.api.dsl.LibraryBuildFeatures,
        com.android.build.api.dsl.LibraryBuildType,
        com.android.build.api.dsl.LibraryDefaultConfig,
        com.android.build.api.dsl.LibraryProductFlavor,
        *,
        *
        >.setupModuleBuildTypes() {
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

private fun BaseExtension.kotlinOptions(configure: Action<KotlinJvmOptions>): Unit =
    (this as org.gradle.api.plugins.ExtensionAware).extensions.configure("kotlinOptions", configure)

fun BaseExtension.setupKotlinOptions() {
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-Xopt-in=kotlin.RequiresOptIn",
            "-Xopt-in=kotlin.ExperimentalStdlibApi",
            "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-Xopt-in=kotlinx.coroutines.FlowPreview",
            "-Xopt-in=kotlin.time.ExperimentalTime",
            "-Xopt-in=kotlin.RequiresOptIn",
            "-Xjvm-default=all",
            "-XXLanguage:+DataObjects",
            "-Xcontext-receivers"
        )
    }
}

fun BaseExtension.setupCompileOptions() {
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

fun Test.setupTestLogging() {
    testLogging {
        events(
            TestLogEvent.FAILED,
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED,
//            TestLogEvent.STANDARD_OUT,
        )
        exceptionFormat = TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true

        addTestListener(object : TestListener {
            override fun beforeSuite(suite: TestDescriptor) {}
            override fun beforeTest(testDescriptor: TestDescriptor) {}
            override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {}
            override fun afterSuite(suite: TestDescriptor, result: TestResult) {
                if (suite.parent != null) {
                    val messages = """
                        ------------------------------------------------------------------------------------------------
                        | ${result.resultType} ${result.testCount} tests: ${result.successfulTestCount} passed, ${result.failedTestCount} failed, ${result.skippedTestCount} skipped)
                        ------------------------------------------------------------------------------------------------
                        
                    """.trimIndent()
                    println(messages)
                }
            }
        })
    }
}
