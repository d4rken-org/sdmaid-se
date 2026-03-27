package eu.darken.sdmse.scheduler.core

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.serialization.SerializedStorage
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleStorage @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    json: Json,
) : SerializedStorage<Set<Schedule>>(dispatcherProvider, json, TAG) {

    override val provideBackupPath: () -> File = {
        val baseDir = File(context.filesDir, "scheduler")
        File(baseDir, "schedules")
    }

    override val provideBackupFileName: () -> String = { "schedules-v1" }

    override val provideSerializer: () -> KSerializer<Set<Schedule>> = { SetSerializer(Schedule.serializer()) }

    companion object {
        private val TAG = logTag("Scheduler", "Storage")
    }
}
