package eu.darken.sdmse.squeezer.core.processor

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.local.LocalPath
import java.io.File
import javax.inject.Inject

/**
 * A narrow, testable surface for the filesystem operations FileTransaction needs.
 *
 * The squeezer compression pipeline is `LocalPath`-only today (Media3 Transformer requires a
 * raw file path for video output, and ImageProcessor sources bitmaps from a `java.io.File`),
 * so this interface still hands out `java.io.File`s rather than `APath`s. That constraint
 * keeps the integration with Media3 simple; delete operations are routed through
 * `GatewaySwitch` so they still benefit from LocalGateway's NORMAL → ROOT → ADB escalation on
 * restricted paths.
 *
 * Splitting this out behind an interface also lets unit tests simulate specific failure modes
 * (e.g. a restore-rename failing) that are hard to force with real `java.io.File` calls.
 */
interface FileOps {
    suspend fun exists(file: File): Boolean
    suspend fun canRead(file: File): Boolean
    suspend fun length(file: File): Long
    suspend fun delete(file: File): Boolean
    suspend fun renameTo(from: File, to: File): Boolean
    suspend fun mkdirs(dir: File): Boolean
    suspend fun createFile(file: File): Boolean
    suspend fun listFiles(dir: File): List<File>
    suspend fun copyFile(from: File, to: File)
}

@Reusable
class GatewayFileOps @Inject constructor(
    private val gatewaySwitch: GatewaySwitch,
) : FileOps {

    override suspend fun exists(file: File): Boolean = file.exists()

    override suspend fun canRead(file: File): Boolean = file.canRead()

    override suspend fun length(file: File): Long = file.length()

    override suspend fun delete(file: File): Boolean {
        // Route deletes through GatewaySwitch so restricted paths (e.g. under /data/media/)
        // automatically escalate to ROOT/ADB via LocalGateway instead of returning a silent
        // false. If the gateway itself fails, fall back to a direct File.delete().
        if (!file.exists()) return true
        return try {
            gatewaySwitch.delete(LocalPath.build(file), recursive = false)
            true
        } catch (e: Throwable) {
            log(TAG, WARN) { "Gateway delete failed for ${file.path}, falling back: ${e.asLog()}" }
            file.delete()
        }
    }

    override suspend fun renameTo(from: File, to: File): Boolean = from.renameTo(to)

    override suspend fun mkdirs(dir: File): Boolean = if (dir.exists()) true else dir.mkdirs()

    override suspend fun createFile(file: File): Boolean = try {
        file.createNewFile()
    } catch (e: Exception) {
        log(TAG, WARN) { "createFile failed for ${file.path}: ${e.message}" }
        false
    }

    override suspend fun listFiles(dir: File): List<File> =
        dir.listFiles()?.toList() ?: emptyList()

    override suspend fun copyFile(from: File, to: File) {
        from.inputStream().use { input ->
            to.outputStream().use { output -> input.copyTo(output) }
        }
    }

    companion object {
        private val TAG = logTag("Squeezer", "FileOps")
    }
}

@InstallIn(SingletonComponent::class)
@Module
abstract class FileOpsModule {
    @Binds
    @Reusable
    abstract fun bindFileOps(impl: GatewayFileOps): FileOps
}
