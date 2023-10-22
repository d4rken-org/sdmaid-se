package eu.darken.sdmse.scheduler.core

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

@JsonClass(generateAdapter = true)
data class Schedule(
    @Json(name = "id") val id: ScheduleId,
    @Json(name = "createdAt") val createdAt: Instant = Instant.now(),
    @Json(name = "scheduledAt") val scheduledAt: Instant? = null,
    @Json(name = "hour") val hour: Int = 22,
    @Json(name = "minute") val minute: Int = 0,
    @Json(name = "label") val label: String = "",
    @Json(name = "repeatInterval") val repeatInterval: Duration = Duration.ofDays(3),
    @Json(name = "corpsefinderEnabled") val useCorpseFinder: Boolean = false,
    @Json(name = "systemcleanerEnabled") val useSystemCleaner: Boolean = false,
    @Json(name = "appcleanerEnabled") val useAppCleaner: Boolean = false,
    @Json(name = "executedAt") val executedAt: Instant? = null,
) {
    val isEnabled: Boolean
        get() = scheduledAt != null

    internal fun calcFirstExecutionAt(
        userZone: ZoneId = ZoneId.systemDefault()
    ): Instant? {
        if (scheduledAt == null) return null

        val scheduledAtZoned = scheduledAt.atZone(userZone)
        return scheduledAtZoned
            .withHour(hour)
            .withMinute(minute)
            .let {
                if (it.isAfter(scheduledAtZoned)) {
                    it.plus(repeatInterval.minus(Duration.ofDays(1)))
                } else {
                    it.plus(repeatInterval)
                }
            }
            .toInstant()
    }

    fun calcExecutionEta(
        now: Instant,
        reschedule: Boolean,
        userZone: ZoneId = ZoneId.systemDefault(),
    ): Duration? {
        if (scheduledAt == null) return null

        fun calcNextTrigger(present: Instant): Instant {
            var nextTrigger = calcFirstExecutionAt(userZone)!!
            while (nextTrigger.isBefore(present)) {
                nextTrigger = nextTrigger.plus(repeatInterval)
            }
            return nextTrigger
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
