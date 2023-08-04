package eu.darken.sdmse.systemcleaner.core.filter.custom

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.Segments
import eu.darken.sdmse.systemcleaner.core.filter.FilterIdentifier
import java.time.Duration
import java.time.Instant

@JsonClass(generateAdapter = true)
data class CustomFilterConfig(
    @Json(name = "configVersion") val configVersion: Int = 1,
    @Json(name = "identifier") val identifier: FilterIdentifier,
    @Json(name = "createdAt") val createdAt: Instant = Instant.now(),
    @Json(name = "label") val label: String,
    @Json(name = "areas") val areas: Set<DataArea.Type>? = null,
    @Json(name = "fileTypes") val fileTypes: Set<FileType>? = null,
    @Json(name = "pathContains") val pathContains: Set<Segments>? = null,
    @Json(name = "pathContainsNot") val exclusion: Set<Segments>? = null,
    @Json(name = "nameContains") val nameContains: Set<String>? = null,
    @Json(name = "nameEndsWith") val nameEndsWith: Set<String>? = null,
    @Json(name = "ageMinimum") val ageMinimum: Duration? = null,
    @Json(name = "ageMaximum") val ageMaximum: Duration? = null,
    @Json(name = "sizeMinimum") val sizeMinimum: Long? = null,
    @Json(name = "sizeMaximum") val sizeMaximum: Long? = null,
) {
    val isUnderdefined: Boolean
        get() = pathContains.isNullOrEmpty() && nameContains.isNullOrEmpty() && nameEndsWith.isNullOrEmpty()
}
