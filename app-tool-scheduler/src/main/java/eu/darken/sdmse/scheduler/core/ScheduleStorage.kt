package eu.darken.sdmse.scheduler.core

import android.content.Context
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.serialization.SerializedStorage
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleStorage @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    private val moshi: Moshi,
) : SerializedStorage<Set<Schedule>>(dispatcherProvider, TAG) {

    override val provideBackupPath: () -> File = {
        val baseDir = File(context.filesDir, "scheduler")
        File(baseDir, "schedules")
    }

    override val provideBackupFileName: () -> String = { "schedules-v1" }

    override val provideAdapter: () -> JsonAdapter<Set<Schedule>> = { moshi.adapter<Set<Schedule>>() }

    companion object {
        private val TAG = logTag("Scheduler", "Storage")
    }
}