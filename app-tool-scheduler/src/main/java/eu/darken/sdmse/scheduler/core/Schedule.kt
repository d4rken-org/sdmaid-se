@file:UseSerializers(InstantSerializer::class, DurationSerializer::class)

package eu.darken.sdmse.scheduler.core

import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.serialization.DurationSerializer
import eu.darken.sdmse.common.serialization.InstantSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

@Serializable
data class Schedule(
    @SerialName("id") val id: ScheduleId,
    @SerialName("createdAt") val createdAt: Instant = Instant.now(),
    @SerialName("scheduledAt") val scheduledAt: Instant? = null,
    @SerialName("hour") val hour: Int = 22,
    @SerialName("minute") val minute: Int = 0,
    @SerialName("label") val label: String = "",
    @SerialName("repeatInterval") val repeatInterval: Duration = Duration.ofDays(3),
    @SerialName("userZone") val userZone: String? = null,
    @SerialName("corpsefinderEnabled") val useCorpseFinder: Boolean = false,
    @SerialName("systemcleanerEnabled") val useSystemCleaner: Boolean = false,
    @SerialName("appcleanerEnabled") val useAppCleaner: Boolean = false,
    @SerialName("commandsAfterSchedule") val commandsAfterSchedule: List<String> = emptyList(),
    @SerialName("executedAt") val executedAt: Instant? = null,
) {
    val isEnabled: Boolean
        get() = scheduledAt != null

    private fun resolveZone(): ZoneId = userZone?.let {
        try {
            ZoneId.of(it)
        } catch (e: Exception) {
            log(SchedulerManager.TAG, WARN) { "Invalid stored timezone '$it', falling back to system default: $e" }
            null
        }
    } ?: ZoneId.systemDefault()

    private fun calcTargetTime(zone: ZoneId) = scheduledAt?.atZone(zone)?.withHour(hour)?.withMinute(minute)

    internal fun calcFirstExecutionAt(
        zone: ZoneId = resolveZone()
    ): Instant? {
        val targetToday = calcTargetTime(zone) ?: return null
        val scheduledAtZoned = scheduledAt!!.atZone(zone)
        val intervalDays = repeatInterval.toDays()

        // Use calendar arithmetic (plusDays) to preserve local time across DST
        return if (targetToday.isAfter(scheduledAtZoned)) {
            // Target time today is still ahead, first execution in (interval - 1) days
            targetToday.plusDays(intervalDays - 1)
        } else {
            // Target time today has passed, first execution in interval days
            targetToday.plusDays(intervalDays)
        }.toInstant()
    }

    fun calcExecutionEta(
        now: Instant,
        reschedule: Boolean,
        zone: ZoneId = resolveZone(),
    ): Duration? {
        if (scheduledAt == null) return null

        val intervalDays = repeatInterval.toDays()

        fun calcNextTrigger(present: Instant): Instant {
            var nextZoned = calcTargetTime(zone) ?: return present
            // Find next occurrence strictly after present (not at or before)
            while (!nextZoned.toInstant().isAfter(present)) {
                // Use plusDays to preserve local time across DST transitions
                nextZoned = nextZoned.plusDays(intervalDays)
            }
            return nextZoned.toInstant()
        }

        return Duration
            .between(now, calcNextTrigger(now))
            .let { eta ->
                if (reschedule && eta < Duration.ofHours(12)) {
                    // `setInitialDelay` could have variance, e.g. due to system power management
                    // Our minimal repeat interval is 1d+, so let's prevent accidental immediate rescheduling
                    log(SchedulerManager.TAG, WARN) { "schedule($label): Premature rescheduling! ETA is only $eta" }
                    val futureNow = now.plus(eta).plus(Duration.ofMinutes(3))
                    Duration.between(now, calcNextTrigger(futureNow))
                } else {
                    eta
                }
            }
    }

}
