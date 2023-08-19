package eu.darken.sdmse.systemcleaner.core.filter.custom

import android.os.Parcelable
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.systemcleaner.core.filter.FilterIdentifier
import eu.darken.sdmse.systemcleaner.core.sieve.NameCriterium
import eu.darken.sdmse.systemcleaner.core.sieve.SegmentCriterium
import kotlinx.parcelize.Parcelize
import java.time.Duration
import java.time.Instant

@Parcelize
@JsonClass(generateAdapter = true)
data class CustomFilterConfig(
    @Json(name = "configVersion") val configVersion: Long = 6L,
    @Json(name = "id") val identifier: FilterIdentifier,
    @Json(name = "createdAt") val createdAt: Instant = Instant.now(),
    @Json(name = "modifiedAt") val modifiedAt: Instant = Instant.now(),
    @Json(name = "label") val label: String,
    @Json(name = "areas") val areas: Set<DataArea.Type>? = null,
    @Json(name = "fileTypes") val fileTypes: Set<FileType>? = null,
    @Json(name = "pathCriteria") val pathCriteria: Set<SegmentCriterium>? = null,
    @Json(name = "pathExclusionCriteria") val exclusionCriteria: Set<SegmentCriterium>? = null,
    @Json(name = "nameCriteria") val nameCriteria: Set<NameCriterium>? = null,
    @Json(name = "sizeMin") val sizeMinimum: Long? = null,
    @Json(name = "sizeMax") val sizeMaximum: Long? = null,
    @Json(name = "ageMin") val ageMinimum: Duration? = null,
    @Json(name = "ageMax") val ageMaximum: Duration? = null,
    @Json(name = "pathRegexes") val pathRegexes: Set<Regex>? = null,
) : Parcelable {
    val isUnderdefined: Boolean
        get() = pathCriteria.isNullOrEmpty() && nameCriteria.isNullOrEmpty()
}
