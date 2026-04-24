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
 * A narrow, testable surface for the filesystem operations [FileTransaction] needs.
 *
 * The squeezer compression pipeline is `LocalPath`-only because Media3 Transformer and
 * (to a lesser extent) BitmapFactory / ExifPreserver hard-require raw filesystem paths.
 * `MediaScanner` and the processors pre-filter candidates via `SqueezerEligibility` at
 * `LocalGateway.Mode.NORMAL`, so everything reaching this interface is best-effort
 * guaranteed to be reachable via plain [File]. Delete operations are still routed through
 * `GatewaySwitch` so they benefit from NORMAL → ROOT → ADB escalation on restricted
 * paths that might briefly require it — but the happy path is fully local.
 *
 * TODO(gateway): rename / copy / exists / length currently delegate to raw `java.io.File`
 * because the gateway does not expose a rename operation today. Adding one (APathGateway +
 * LocalGateway + FileOpsConnection.aidl + SAFGateway) would let this interface move to
 * `APath`, but only becomes useful once Media3 / BitmapFactory / ExifPreserver accept
 * non-File inputs.
 *
 * Splitting this out behind an interface also lets unit tests simulate specific failure
 * modes (e.g. a restore-rename failing) that are hard to force with real [File] calls.
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
    suspend fun getLastModified(file: File): Long
    suspend fun setLastModified(file: File, time: Long): Boolean
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

    override suspend fun getLastModified(file: File): Long = file.lastModified()

    override suspend fun setLastModified(file: File, time: Long): Boolean = try {
        file.setLastModified(time)
    } catch (e: Exception) {
        log(TAG, WARN) { "setLastModified failed for ${file.path}: ${e.message}" }
        false
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
