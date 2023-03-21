package eu.darken.sdmse.scheduler.core

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
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

    val firstExecution: Instant?
        get() {
            if (scheduledAt == null) return null
            return scheduledAt
                .atZone(ZoneId.systemDefault())
                .withHour(hour)
                .withMinute(minute)
                .let {
                    if (it.isAfter(scheduledAt.atZone(ZoneId.systemDefault()))) {
                        it.plus(repeatInterval.minus(Duration.ofDays(1)))
                    } else {
                        it.plus(repeatInterval)
                    }
                }
                .toInstant()
        }

    val nextExecution: Instant?
        get() {
            if (scheduledAt == null) return null

            var nextTrigger = firstExecution!!
            while (nextTrigger.isBefore(Instant.now())) {
                nextTrigger = nextTrigger.plus(repeatInterval)
            }
            return nextTrigger
        }
}
