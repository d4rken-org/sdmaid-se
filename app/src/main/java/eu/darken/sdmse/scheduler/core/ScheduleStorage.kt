package eu.darken.sdmse.scheduler.core

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.local.fromFile
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.IOException
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleStorage @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    private val moshi: Moshi,
) {

    private val saveDir by lazy {
        val baseDir = File(context.filesDir, "scheduler")
        File(baseDir, "schedules").apply { mkdirs() }
    }
    private val saveCurrent by lazy { File(saveDir, "schedules-v1.json") }
    private val saveBackup by lazy { File(saveDir, "schedules-v1.json.backup") }

    private val adapter by lazy { moshi.adapter<Set<Schedule>>() }

    private val lock = Mutex()

    suspend fun save(schedules: Set<Schedule>): Unit = lock.withLock {
        log(TAG) { "save(): ${schedules.size}" }
        withContext(NonCancellable + dispatcherProvider.IO) {
            if (saveCurrent.exists()) {
                saveCurrent.copyTo(saveBackup, overwrite = true)
            }
            try {
                val rawJson = adapter.toJson(schedules)
                saveCurrent.writeText(rawJson)
            } catch (e: IOException) {
                log(TAG, ERROR) { "Saving schedules failed: ${e.asLog()}" }
                saveBackup.copyTo(saveCurrent, overwrite = true)
                saveBackup.delete()
            }
        }
    }

    suspend fun load(): Set<Schedule> = lock.withLock {
        val schedules = withContext(dispatcherProvider.IO) {
            if (saveCurrent.exists()) {
                adapter.fromFile(saveCurrent)
            } else {
                emptySet()
            }
        }
        log(TAG) { "load(): ${schedules.size}" }
        return schedules
    }

    companion object {
        private val TAG = logTag("Scheduler", "Storage")
    }
}