package eu.darken.sdmse.scheduler.core

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Schedule(
    @Json(name = "id") val id: String,
    @Json(name = "label") val label: String = "",
    @Json(name = "useCorpseFinder") val useCorpseFinder: Boolean = true,
    @Json(name = "useSystemCleaner") val useSystemCleaner: Boolean = true,
    @Json(name = "useAppCleaner") val useAppCleaner: Boolean = true,
)
