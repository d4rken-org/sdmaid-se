package eu.darken.sdmse.common.sieve

import com.squareup.moshi.JsonClass
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import eu.darken.sdmse.common.files.Segments
import eu.darken.sdmse.common.files.containsSegments
import eu.darken.sdmse.common.files.endsWith
import eu.darken.sdmse.common.files.isAncestorOf
import eu.darken.sdmse.common.files.matches
import eu.darken.sdmse.common.files.startsWith
import eu.darken.sdmse.common.files.toSegs
import kotlinx.parcelize.Parcelize


@JsonClass(generateAdapter = true)
@Parcelize
data class SegmentCriterium(
    val segments: Segments,
    override val mode: Mode,
) : SieveCriterium {

    constructor(rawSegments: String, mode: Mode) : this(rawSegments.toSegs(), mode)

    fun matchRaw(rawSegments: String) = match(rawSegments.toSegs())

    fun match(target: Segments): Boolean = when (mode) {
        is Mode.Ancestor -> segments.isAncestorOf(target, ignoreCase = mode.ignoreCase)
        is Mode.Start -> target.startsWith(segments, ignoreCase = mode.ignoreCase, allowPartial = mode.allowPartial)
        is Mode.End -> target.endsWith(segments, ignoreCase = mode.ignoreCase, allowPartial = mode.allowPartial)
        is Mode.Contain -> target.containsSegments(
            segments,
            allowPartial = mode.allowPartial,
            ignoreCase = mode.ignoreCase
        )

        is Mode.Equal -> target.matches(segments, ignoreCase = mode.ignoreCase)
    }

    sealed interface Mode : SieveCriterium.Mode {

        @JsonClass(generateAdapter = true)
        @Parcelize
        data class Ancestor(
            val ignoreCase: Boolean = true,
        ) : Mode

        @JsonClass(generateAdapter = true)
        @Parcelize
        data class Start(
            val ignoreCase: Boolean = true,
            val allowPartial: Boolean = false,
        ) : Mode

        @JsonClass(generateAdapter = true)
        @Parcelize
        data class Contain(
            val ignoreCase: Boolean = true,
            val allowPartial: Boolean = false,
        ) : Mode

        @JsonClass(generateAdapter = true)
        @Parcelize
        data class End(
            val ignoreCase: Boolean = true,
            val allowPartial: Boolean = false,
        ) : Mode

        @JsonClass(generateAdapter = true)
        @Parcelize
        data class Equal(
            val ignoreCase: Boolean = true,
        ) : Mode
    }

    companion object {
        val MOSHI_ADAPTER_FACTORY = PolymorphicJsonAdapterFactory.of(Mode::class.java, "type")
            .withSubtype(Mode.Ancestor::class.java, "ANCESTOR")
            .withSubtype(Mode.Start::class.java, "START")
            .withSubtype(Mode.Contain::class.java, "CONTAIN")
            .withSubtype(Mode.End::class.java, "END")
            .withSubtype(Mode.Equal::class.java, "MATCH")!!
    }
}