package eu.darken.sdmse.main.core.motd

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.UUID

@JsonClass(generateAdapter = true)
data class Motd(
    @Json(name = "id") val id: UUID,
    @Json(name = "message") val message: String,
    @Json(name = "primaryLink") val primaryLink: String?,
    @Json(name = "versionMinimum") val minimumVersion: Long?,
)
