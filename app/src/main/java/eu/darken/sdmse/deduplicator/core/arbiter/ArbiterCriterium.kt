package eu.darken.sdmse.deduplicator.core.arbiter

sealed interface ArbiterCriterium {

    sealed interface Mode

    data class DuplicateType(
        val mode: Mode = Mode.PREFER_CHECKSUM
    ) : ArbiterCriterium {
        enum class Mode : ArbiterCriterium.Mode {
            PREFER_CHECKSUM,
            PREFER_PHASH,
        }
    }

    data class MediaProvider(
        val mode: Mode = Mode.PREFER_INDEXED
    ) : ArbiterCriterium {
        enum class Mode : ArbiterCriterium.Mode {
            PREFER_INDEXED,
            PREFER_UNKNOWN,
        }
    }

    data class Location(
        val mode: Mode = Mode.PREFER_PRIMARY
    ) : ArbiterCriterium {
        enum class Mode : ArbiterCriterium.Mode {
            PREFER_PRIMARY,
            PREFER_SECONDARY,
        }
    }

    data class Nesting(
        val mode: Mode = Mode.PREFER_SHALLOW
    ) : ArbiterCriterium {
        enum class Mode : ArbiterCriterium.Mode {
            PREFER_SHALLOW,
            PREFER_DEEPER,
        }
    }

    data class Modified(
        val mode: Mode = Mode.PREFER_OLDER
    ) : ArbiterCriterium {
        enum class Mode : ArbiterCriterium.Mode {
            PREFER_OLDER,
            PREFER_NEWER,
        }
    }

    data class Size(
        val mode: Mode = Mode.PREFER_LARGER
    ) : ArbiterCriterium {
        enum class Mode : ArbiterCriterium.Mode {
            PREFER_LARGER,
            PREFER_SMALLER,
        }
    }
}