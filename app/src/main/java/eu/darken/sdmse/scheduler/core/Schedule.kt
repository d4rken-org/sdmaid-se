package eu.darken.sdmse.scheduler.core

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Schedule(
    @Json(name = "id") val id: String,
    @Json(name = "hour") val hour: Int = 22,
    @Json(name = "minute") val minute: Int = 0,
    @Json(name = "label") val label: String = "",
    @Json(name = "repeat_interval_ms") val repeatIntervalMs: Long = 259200000L,
    @Json(name = "isEnabled") val isEnabled: Boolean = false,
    @Json(name = "useCorpseFinder") val useCorpseFinder: Boolean = false,
    @Json(name = "useSystemCleaner") val useSystemCleaner: Boolean = false,
    @Json(name = "useAppCleaner") val useAppCleaner: Boolean = false,
)
