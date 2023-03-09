package eu.darken.sdmse.scheduler.core

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.Duration
import java.time.Instant

@JsonClass(generateAdapter = true)
data class Schedule(
    @Json(name = "id") val id: String,
    @Json(name = "created_at") val createdAt: Instant = Instant.now(),
    @Json(name = "scheduled_at") val scheduledAt: Instant? = null,
    @Json(name = "hour") val hour: Int = 22,
    @Json(name = "minute") val minute: Int = 0,
    @Json(name = "label") val label: String = "",
    @Json(name = "repeat_interval") val repeatInterval: Duration = Duration.ofDays(3),
    @Json(name = "useCorpseFinder") val useCorpseFinder: Boolean = false,
    @Json(name = "useSystemCleaner") val useSystemCleaner: Boolean = false,
    @Json(name = "useAppCleaner") val useAppCleaner: Boolean = false,
) {
    val isEnabled: Boolean
        get() = scheduledAt != null
}
