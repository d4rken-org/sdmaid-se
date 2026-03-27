package eu.darken.sdmse.systemcleaner.core.filter.custom

import android.os.Parcelable
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.serialization.DurationSerializer
import eu.darken.sdmse.common.serialization.InstantSerializer
import eu.darken.sdmse.common.serialization.RegexSerializer
import eu.darken.sdmse.common.sieve.NameCriterium
import eu.darken.sdmse.common.sieve.SegmentCriterium
import eu.darken.sdmse.systemcleaner.core.filter.FilterIdentifier
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant

@Serializable
@Parcelize
data class CustomFilterConfig(
    @SerialName("configVersion") val configVersion: Long = 6L,
    @SerialName("id") val identifier: FilterIdentifier,
    @SerialName("createdAt") @Serializable(with = InstantSerializer::class) val createdAt: Instant = Instant.now(),
    @SerialName("modifiedAt") @Serializable(with = InstantSerializer::class) val modifiedAt: Instant = Instant.now(),
    @SerialName("label") val label: String,
    @SerialName("areas") val areas: Set<DataArea.Type>? = null,
    @SerialName("fileTypes") val fileTypes: Set<FileType>? = null,
    @SerialName("pathCriteria") val pathCriteria: Set<SegmentCriterium>? = null,
    @SerialName("pathExclusionCriteria") val exclusionCriteria: Set<SegmentCriterium>? = null,
    @SerialName("nameCriteria") val nameCriteria: Set<NameCriterium>? = null,
    @SerialName("sizeMin") val sizeMinimum: Long? = null,
    @SerialName("sizeMax") val sizeMaximum: Long? = null,
    @SerialName("ageMin") @Serializable(with = DurationSerializer::class) val ageMinimum: Duration? = null,
    @SerialName("ageMax") @Serializable(with = DurationSerializer::class) val ageMaximum: Duration? = null,
    @SerialName("pathRegexes") val pathRegexes: Set<@Serializable(with = RegexSerializer::class) Regex>? = null,
) : Parcelable {
    val isUnderdefined: Boolean
        get() = pathCriteria.isNullOrEmpty() && nameCriteria.isNullOrEmpty()

    val isDefault: Boolean
        get() = this == CustomFilterConfig(
            identifier = identifier,
            createdAt = createdAt,
            modifiedAt = modifiedAt,
            label = "",
        )
}
