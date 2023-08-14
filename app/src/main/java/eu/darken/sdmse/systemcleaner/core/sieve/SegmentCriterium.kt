package eu.darken.sdmse.systemcleaner.core.sieve

import eu.darken.sdmse.common.files.Segments

data class SegmentCriterium(
    val segments: Segments,
    val mode: Mode,
) : SieveCriterium {

    sealed interface Mode {
        data class Ancestor(
            val ignoreCase: Boolean = true,
        ) : Mode

        data class Start(
            val ignoreCase: Boolean = true,
            val allowPartial: Boolean = false,
        ) : Mode

        data class Contain(
            val ignoreCase: Boolean = true,
            val allowPartial: Boolean = false,
        ) : Mode

        data class End(
            val ignoreCase: Boolean = true,
            val allowPartial: Boolean = false,
        ) : Mode

        data class Match(
            val ignoreCase: Boolean = true,
        ) : Mode
    }
}