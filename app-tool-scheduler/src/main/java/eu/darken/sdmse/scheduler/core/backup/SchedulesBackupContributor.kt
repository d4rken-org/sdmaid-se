package eu.darken.sdmse.scheduler.core.backup

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.backup.ConfigBackupContributor
import eu.darken.sdmse.common.backup.RestoreMode
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.scheduler.core.Schedule
import eu.darken.sdmse.scheduler.core.ScheduleStorage
import eu.darken.sdmse.scheduler.core.SchedulerManager
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backs up schedule entries. Restore goes through [SchedulerManager.saveSchedule] (not the raw
 * storage) so WorkManager is re-armed — the manager recomputes the next future trigger from the
 * restored `scheduledAt`/interval, so a stale timestamp is fine.
 */
@Singleton
class SchedulesBackupContributor @Inject constructor(
    private val scheduleStorage: ScheduleStorage,
    private val schedulerManager: SchedulerManager,
    private val json: Json,
) : ConfigBackupContributor {

    override val key = "schedules"
    override val restoreOrder = ConfigBackupContributor.ORDER_CONTENT

    private val serializer = SetSerializer(Schedule.serializer())

    override suspend fun snapshot(): JsonElement? {
        val current = scheduleStorage.load() ?: emptySet()
        log(TAG) { "snapshot(): ${current.size} schedules" }
        if (current.isEmpty()) return null
        return json.encodeToJsonElement(serializer, current)
    }

    override suspend fun validate(data: JsonElement) {
        json.decodeFromJsonElement(serializer, data)
    }

    override suspend fun restore(data: JsonElement, mode: RestoreMode) {
        val restored = json.decodeFromJsonElement(serializer, data).map { it.sanitized() }.toSet()
        log(TAG) { "restore(mode=$mode): ${restored.size} schedules" }
        when (mode) {
            // Atomic swap — a failure can't leave a half-replaced schedule set.
            RestoreMode.REPLACE -> schedulerManager.setSchedules(restored)
            RestoreMode.MERGE -> restored.forEach { schedulerManager.saveSchedule(it) }
        }
    }

    /** The UI enforces a 1+ day interval; a (possibly hand-edited) restored schedule must too. */
    private fun Schedule.sanitized(): Schedule = if (repeatInterval < MIN_INTERVAL) {
        log(TAG, WARN) { "restore(): clamping invalid interval $repeatInterval of '$id' to $MIN_INTERVAL" }
        copy(repeatInterval = MIN_INTERVAL)
    } else {
        this
    }

    companion object {
        private val TAG = logTag("Backup", "Schedules")
        private val MIN_INTERVAL: Duration = Duration.ofDays(1)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SchedulesBackupModule {
    @Binds
    @IntoSet
    abstract fun bind(c: SchedulesBackupContributor): ConfigBackupContributor
}
