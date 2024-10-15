package eu.darken.sdmse.common.coil

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.local.listFiles2
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoilTempFiles @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
) {

    private val basePath: File = File(context.cacheDir, "coil")
    private val legacyPath: File = context.cacheDir

    suspend fun getBaseCachePath(): File {
        val path = legacyPath.apply {
            if (mkdirs()) log(TAG) { "Cache path was created: $this" }
        }
        return path
    }

    suspend fun cleanUp() = withContext(dispatcherProvider.IO + NonCancellable) {
        if (!basePath.exists()) return@withContext

        try {
            log(TAG) { "Checking for legacy files in $legacyPath" }
            legacyPath.listFiles2()
                .filter { NAME_REGEX.matches(it.name) }
                .forEach {
                    log(TAG) { "Deleting legacy tmp file: $it (${it.length()} Byte)" }
                    if (it.delete()) log(TAG, VERBOSE) { "Deleted legacy file: $it" }
                }
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to clean up legacy files $basePath:\n${e.asLog()}" }
        }

        try {
            log(TAG) { "Cleaning up $basePath" }
            basePath.listFiles2().forEach {
                log(TAG) { "Deleting orphaned tmp file: $it (${it.length()} Byte)" }
                if (it.delete()) log(TAG, VERBOSE) { "Deleted: $it" }
            }
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to clean up $basePath:\n${e.asLog()}" }
        }
    }

    companion object {
        private val TAG = logTag("Coil", "TempFiles")
        internal val NAME_REGEX = Regex("""tmp\d+\.tmp""")
    }
}