package eu.darken.sdmse.common.serialization

import com.squareup.moshi.JsonAdapter
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

abstract class SerializedStorage<T>(
    private val dispatcherProvider: DispatcherProvider,
    private val logTag: String,
) {
    abstract val provideBackupPath: () -> File
    abstract val provideBackupFileName: () -> String

    private val saveCurrent by lazy {
        File(provideBackupPath(), "${provideBackupFileName()}.json").also {
            it.parentFile!!.mkdirs()
        }
    }
    private val saveBackup by lazy {
        File(provideBackupPath(), "${provideBackupFileName()}.json.backup").also {
            it.parentFile!!.mkdirs()
        }
    }

    abstract val provideAdapter: () -> JsonAdapter<T>
    private val adapter by lazy { provideAdapter() }

    private val lock = Mutex()

    suspend fun save(data: T): Unit = lock.withLock {
        log(logTag) { "save(): $data" }
        withContext(NonCancellable + dispatcherProvider.IO) {
            if (saveCurrent.exists()) {
                saveCurrent.copyTo(saveBackup, overwrite = true)
            }
            try {
                val rawJson = adapter.toJson(data)
                saveCurrent.writeText(rawJson)
            } catch (e: IOException) {
                log(logTag, ERROR) { "Saving failed: ${e.asLog()}" }
                saveBackup.copyTo(saveCurrent, overwrite = true)
                saveBackup.delete()
            }
        }
    }

    private fun tryLoadFromFile(file: File): T? = try {
        adapter.fromFile(file)
    } catch (e: Exception) {
        log(logTag, ERROR) { "Failed to load from $file: ${e.asLog()}" }
        null
    }

    suspend fun load(): T? = lock.withLock {
        var data: T? = null
        withContext(dispatcherProvider.IO) {
            if (!saveCurrent.exists()) return@withContext

            data = tryLoadFromFile(saveCurrent)

            if (data == null && saveBackup.exists()) {
                log(logTag) { "Main file corrupt, trying backup: $saveBackup" }
                data = tryLoadFromFile(saveBackup)
                if (data != null) {
                    saveBackup.copyTo(saveCurrent, overwrite = true)
                    saveBackup.delete()
                }
            }

            if (data == null) {
                saveCurrent.delete()
                saveBackup.delete()
            }
        }
        log(logTag) { "load(): $data" }
        return data
    }
}