package eu.darken.sdmse.exclusion.core.types

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.files.Segments
import eu.darken.sdmse.common.files.containsSegments
import eu.darken.sdmse.common.files.joinSegments

@JsonClass(generateAdapter = true)
data class SegmentExclusion(
    @Json(name = "segments") val segments: Segments,
    @Json(name = "allowPartial") val allowPartial: Boolean,
    @Json(name = "ignoreCase") val ignoreCase: Boolean,
    @Json(name = "tags") override val tags: Set<Exclusion.Tag> = setOf(Exclusion.Tag.GENERAL)
) : Exclusion.Segment {

    override val id: ExclusionId
        get() = createId(segments)

    override val label: CaString
        get() = caString { segments.joinSegments() }

    override suspend fun match(segments: Segments): Boolean = segments.containsSegments(
        other = this.segments,
        allowPartial = allowPartial,
        ignoreCase = ignoreCase,
    )

    companion object {
        fun createId(segments: Segments): ExclusionId =
            "${SegmentExclusion::class.simpleName}-${segments.joinToString("/")}"
    }
}
