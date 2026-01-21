package eu.darken.sdmse.deduplicator.core.arbiter

import androidx.annotation.StringRes
import com.squareup.moshi.JsonClass
import eu.darken.sdmse.R
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.serialization.ValueBasedPolyJsonAdapterFactory

sealed interface ArbiterCriterium {

    fun criteriumMode(): Mode? = null

    sealed interface Mode {
        @get:StringRes val labelRes: Int
    }

    @JsonClass(generateAdapter = true)
    data class DuplicateType(
        val mode: Mode = Mode.PREFER_CHECKSUM,
    ) : ArbiterCriterium {
        override fun criteriumMode(): Mode = mode

        enum class Mode(@StringRes override val labelRes: Int) : ArbiterCriterium.Mode {
            PREFER_CHECKSUM(R.string.deduplicator_arbiter_mode_prefer_checksum),
            PREFER_PHASH(R.string.deduplicator_arbiter_mode_prefer_phash),
        }
    }

    @JsonClass(generateAdapter = true)
    data class MediaProvider(
        val mode: Mode = Mode.PREFER_INDEXED,
    ) : ArbiterCriterium {
        override fun criteriumMode(): Mode = mode

        enum class Mode(@StringRes override val labelRes: Int) : ArbiterCriterium.Mode {
            PREFER_INDEXED(R.string.deduplicator_arbiter_mode_prefer_indexed),
            PREFER_UNKNOWN(R.string.deduplicator_arbiter_mode_prefer_unknown),
        }
    }

    @JsonClass(generateAdapter = true)
    data class Location(
        val mode: Mode = Mode.PREFER_PRIMARY,
    ) : ArbiterCriterium {
        override fun criteriumMode(): Mode = mode

        enum class Mode(@StringRes override val labelRes: Int) : ArbiterCriterium.Mode {
            PREFER_PRIMARY(R.string.deduplicator_arbiter_mode_prefer_primary),
            PREFER_SECONDARY(R.string.deduplicator_arbiter_mode_prefer_secondary),
        }
    }

    @JsonClass(generateAdapter = true)
    data class Nesting(
        val mode: Mode = Mode.PREFER_SHALLOW,
    ) : ArbiterCriterium {
        override fun criteriumMode(): Mode = mode

        enum class Mode(@StringRes override val labelRes: Int) : ArbiterCriterium.Mode {
            PREFER_SHALLOW(R.string.deduplicator_arbiter_mode_prefer_shallow),
            PREFER_DEEPER(R.string.deduplicator_arbiter_mode_prefer_deeper),
        }
    }

    @JsonClass(generateAdapter = true)
    data class Modified(
        val mode: Mode = Mode.PREFER_OLDER,
    ) : ArbiterCriterium {
        override fun criteriumMode(): Mode = mode

        enum class Mode(@StringRes override val labelRes: Int) : ArbiterCriterium.Mode {
            PREFER_OLDER(R.string.deduplicator_arbiter_mode_prefer_older),
            PREFER_NEWER(R.string.deduplicator_arbiter_mode_prefer_newer),
        }
    }

    @JsonClass(generateAdapter = true)
    data class Size(
        val mode: Mode = Mode.PREFER_LARGER,
    ) : ArbiterCriterium {
        override fun criteriumMode(): Mode = mode

        enum class Mode(@StringRes override val labelRes: Int) : ArbiterCriterium.Mode {
            PREFER_LARGER(R.string.deduplicator_arbiter_mode_prefer_larger),
            PREFER_SMALLER(R.string.deduplicator_arbiter_mode_prefer_smaller),
        }
    }

    @JsonClass(generateAdapter = true)
    data class PreferredPath(
        val keepPreferPaths: Set<APath> = emptySet(),
    ) : ArbiterCriterium

    companion object {
        val MOSHI_FACTORY: ValueBasedPolyJsonAdapterFactory<ArbiterCriterium> =
            ValueBasedPolyJsonAdapterFactory.of(ArbiterCriterium::class.java, "criteriumType")
                .withSubtype(DuplicateType::class.java, "DUPLICATE_TYPE")
                .withSubtype(MediaProvider::class.java, "MEDIA_PROVIDER")
                .withSubtype(Location::class.java, "LOCATION")
                .withSubtype(Nesting::class.java, "NESTING")
                .withSubtype(Modified::class.java, "MODIFIED")
                .withSubtype(Size::class.java, "SIZE")
                .withSubtype(PreferredPath::class.java, "PREFERRED_PATH")
    }
}
