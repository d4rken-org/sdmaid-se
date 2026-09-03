import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.File
import java.io.FileInputStream
import java.util.Properties
import javax.inject.Inject

/**
 * Re-signs the gplay APK with the Google Play app signing key.
 * The gplay variant itself is signed with the upload key (required for the Play AAB),
 * so a directly installable APK needs this second signature.
 *
 * Runs as a finalizer of the gplay assemble tasks; without readable credentials
 * (e.g. on CI) it clears its output and does nothing.
 */
abstract class SignGplayApkTask @Inject constructor(
    private val execOps: ExecOperations,
) : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val apkDir: DirectoryProperty

    /**
     * Name of the upload-key APK inside [apkDir]. Set from the same expression that names the
     * variant output, so a stale APK left in that directory by an earlier build is never picked up.
     */
    @get:Input
    abstract val apkName: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val signingProps: ConfigurableFileCollection

    @get:Internal
    abstract val sdkDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    init {
        // The keystore and apksigner are resolved at execution time and not tracked as
        // inputs, so never trust previous output.
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun sign() {
        val outDir = outputDir.get().asFile.apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }

        val propsFile = signingProps.singleFile
        if (!propsFile.canRead()) {
            logger.lifecycle("No Google Play signing credentials at $propsFile, skipping APK re-sign.")
            return
        }

        val props = Properties().apply {
            FileInputStream(propsFile).use { load(it) }
        }
        val storeFile = File(requireNotNull(props.getProperty("release.storePath")))
        require(storeFile.exists()) { "Keystore not found: $storeFile" }
        val storePassword = requireNotNull(props.getProperty("release.storePassword"))
        val keyAlias = requireNotNull(props.getProperty("release.keyAlias"))
        val keyPassword = requireNotNull(props.getProperty("release.keyPassword"))

        val apksigner = findApksigner()

        val apk = File(apkDir.get().asFile, apkName.get())
        require(apk.exists()) { "APK not found: $apk" }

        val outFile = File(outDir, apk.name.replace("-UPLOAD", ""))
        execOps.exec {
            commandLine(
                apksigner.absolutePath, "sign",
                "--ks", storeFile.absolutePath,
                "--ks-key-alias", keyAlias,
                "--ks-pass", "env:SDMSE_SIGN_KS_PASS",
                "--key-pass", "env:SDMSE_SIGN_KEY_PASS",
                "--v4-signing-enabled", "false",
                "--out", outFile.absolutePath,
                apk.absolutePath,
            )
            environment("SDMSE_SIGN_KS_PASS", storePassword)
            environment("SDMSE_SIGN_KEY_PASS", keyPassword)
        }
        execOps.exec { commandLine(apksigner.absolutePath, "verify", outFile.absolutePath) }
        logger.lifecycle("Signed with Google Play key: $outFile")
    }

    private fun findApksigner(): File {
        val buildTools = sdkDir.get().asFile.resolve("build-tools")
        val candidates = buildTools
            .listFiles { dir: File -> EXECUTABLES.any { File(dir, it).exists() } }
            .orEmpty()
        require(candidates.isNotEmpty()) { "No build-tools with apksigner found in $buildTools" }
        val newest = candidates.maxWith(compareBy(VERSION_COMPARATOR) { it.name })
        return EXECUTABLES.map { File(newest, it) }.first { it.exists() }
    }

    companion object {
        private val EXECUTABLES = listOf("apksigner", "apksigner.bat")

        // Orders e.g. 36.0.0 < 37.0.0-rc1 < 37.0.0-rc2 < 37.0.0
        private val VERSION_COMPARATOR = Comparator<String> { a, b ->
            val (aBase, aPreview) = versionKey(a)
            val (bBase, bPreview) = versionKey(b)
            (0 until maxOf(aBase.size, bBase.size))
                .map { aBase.getOrElse(it) { 0 }.compareTo(bBase.getOrElse(it) { 0 }) }
                .firstOrNull { it != 0 }
                ?: aPreview.compareTo(bPreview)
        }

        private fun versionKey(name: String): Pair<List<Int>, Int> {
            val base = name.substringBefore('-').split('.').mapNotNull { it.toIntOrNull() }
            val preview = name.substringAfter('-', "").filter { it.isDigit() }.toIntOrNull()
            return base to (preview ?: Int.MAX_VALUE)
        }
    }
}
