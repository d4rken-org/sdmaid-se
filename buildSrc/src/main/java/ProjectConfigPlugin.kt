import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File
import java.io.FileInputStream
import java.util.Properties

open class ProjectConfig {
    val packageName = "eu.darken.sdmse"
    val minSdk = 26

    val compileSdk = 35
    val compileSdkPreview: String? = null
    val targetSdk = 35
    val targetSdkPreview: String? = null

    lateinit var version: Version

    override fun toString(): String {
        return "ProjectConfig($packageName, min=$minSdk, compile=$compileSdk, target=$targetSdk, version=$version)"
    }

    fun init(project: Project) {
        val versionProperties = Properties().apply {
            val propsPath = File(project.rootDir, "version.properties")
            println("Version: From $propsPath:")
            load(FileInputStream(propsPath))
            println("$this")
        }
        version = Version(
            major = versionProperties.getProperty("project.versioning.major").toInt(),
            minor = versionProperties.getProperty("project.versioning.minor").toInt(),
            patch = versionProperties.getProperty("project.versioning.patch").toInt(),
            build = versionProperties.getProperty("project.versioning.build").toInt(),
            type = versionProperties.getProperty("project.versioning.type"),
        )
    }

    data class Version(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val build: Int,
        val type: String,
    ) {
        val name: String
            get() = "${major}.${minor}.${patch}-$type${build}"
        val code: Long
            get() = major * 10000000 + minor * 100000 + patch * 1000 + build * 10L
    }
}

class ProjectConfigPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("projectConfig", ProjectConfig::class.java)
        extension.init(project)
        project.afterEvaluate { println("ProjectConfigPlugin loaded: $extension") }
    }
}