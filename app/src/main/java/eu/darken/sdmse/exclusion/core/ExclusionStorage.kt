package eu.darken.sdmse.exclusion.core

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.local.fromFile
import eu.darken.sdmse.exclusion.core.types.Exclusion
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.IOException
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExclusionStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi,
) {
    private val saveDir by lazy {
        File(context.filesDir, "exclusions").apply { mkdirs() }
    }
    private val saveCurrent by lazy { File(saveDir, "exclusions-v1.json") }
    private val saveBackup by lazy { File(saveDir, "exclusions-v1.json.backup") }

    private val adapter by lazy { moshi.adapter<Set<Exclusion>>() }

    private val lock = Mutex()

    suspend fun save(exclusions: Set<Exclusion>): Unit = lock.withLock {
        log(TAG) { "save(): ${exclusions.size}" }
        withContext(NonCancellable) {
            if (saveCurrent.exists()) {
                saveCurrent.copyTo(saveBackup, overwrite = true)
            }
            try {
                val rawJson = adapter.toJson(exclusions)
                saveCurrent.writeText(rawJson)
            } catch (e: IOException) {
                log(TAG, ERROR) { "Saving exclusions failed: ${e.asLog()}" }
                saveBackup.copyTo(saveCurrent, overwrite = true)
                saveBackup.delete()
            }
        }
    }

    suspend fun load(): Set<Exclusion> = lock.withLock {
        val exclusions = if (saveCurrent.exists()) {
            adapter.fromFile(saveCurrent)
        } else {
            emptySet()
        }
        log(TAG) { "load(): ${exclusions.size}" }
        return exclusions
    }

    companion object {
        private val TAG = logTag("Exclusion", "Storage")
    }
}