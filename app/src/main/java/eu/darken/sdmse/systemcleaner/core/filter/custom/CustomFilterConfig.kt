package eu.darken.sdmse.systemcleaner.core.filter.custom

import android.os.Parcelable
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.Segments
import eu.darken.sdmse.systemcleaner.core.BaseSieve
import eu.darken.sdmse.systemcleaner.core.filter.FilterIdentifier
import kotlinx.parcelize.Parcelize
import java.time.Duration
import java.time.Instant

@JsonClass(generateAdapter = true)
data class CustomFilterConfig(
    @Json(name = "configVersion") val configVersion: Int = 1,
    @Json(name = "identifier") val identifier: FilterIdentifier,
    @Json(name = "createdAt") val createdAt: Instant = Instant.now(),
    @Json(name = "modifiedAt") val modifiedAt: Instant = Instant.now(),
    @Json(name = "label") val label: String,
    @Json(name = "areas") val areas: Set<DataArea.Type>? = null,
    @Json(name = "fileTypes") val fileTypes: Set<FileType>? = null,
    @Json(name = "pathCriteria") val pathCriteria: Set<SegmentCriterium>? = null,
    @Json(name = "pathCriteriaNot") val exclusion: Set<SegmentCriterium>? = null,
    @Json(name = "nameCriteria") val nameCriteria: Set<NameCriterium>? = null,
    @Json(name = "ageMinimum") val ageMinimum: Duration? = null,
    @Json(name = "ageMaximum") val ageMaximum: Duration? = null,
    @Json(name = "sizeMinimum") val sizeMinimum: Long? = null,
    @Json(name = "sizeMaximum") val sizeMaximum: Long? = null,
) {

    @JsonClass(generateAdapter = true)
    @Parcelize
    data class NameCriterium(
        @Json(name = "name") val name: String,
        @Json(name = "mode") override val mode: Criterium.Mode,
        @Json(name = "ignoreCase") val ignoreCase: Boolean = true,
    ) : Parcelable, Criterium {

        fun toSieveCriterium() = BaseSieve.NameCriterium(
            name = name,
            mode = when (mode) {
                Criterium.Mode.STARTS -> BaseSieve.Criterium.Mode.STARTS
                Criterium.Mode.CONTAINS -> BaseSieve.Criterium.Mode.CONTAINS
                Criterium.Mode.ENDS -> BaseSieve.Criterium.Mode.ENDS
                Criterium.Mode.MATCHES -> BaseSieve.Criterium.Mode.MATCHES
            },
            ignoreCase = ignoreCase,
        )
    }

    @JsonClass(generateAdapter = true)
    @Parcelize
    data class SegmentCriterium(
        @Json(name = "segments") val segments: Segments,
        @Json(name = "mode") override val mode: Criterium.Mode,
        @Json(name = "allowPartial") val allowPartial: Boolean = false,
        @Json(name = "ignoreCase") val ignoreCase: Boolean = true,
    ) : Parcelable, Criterium {

        fun toSieveCriterium() = BaseSieve.SegmentCriterium(
            segments = segments,
            mode = when (mode) {
                Criterium.Mode.STARTS -> BaseSieve.Criterium.Mode.STARTS
                Criterium.Mode.CONTAINS -> BaseSieve.Criterium.Mode.CONTAINS
                Criterium.Mode.ENDS -> BaseSieve.Criterium.Mode.ENDS
                Criterium.Mode.MATCHES -> BaseSieve.Criterium.Mode.MATCHES
            },
            allowPartial = allowPartial,
            ignoreCase = ignoreCase,
        )
    }

    interface Criterium {
        val mode: Mode

        @JsonClass(generateAdapter = false)
        enum class Mode {
            @Json(name = "STARTS") STARTS,
            @Json(name = "CONTAINS") CONTAINS,
            @Json(name = "ENDS") ENDS,
            @Json(name = "MATCHES") MATCHES,
            ;
        }
    }

    val isUnderdefined: Boolean
        get() = pathCriteria.isNullOrEmpty() && nameCriteria.isNullOrEmpty()
}
