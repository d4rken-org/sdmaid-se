@file:UseSerializers(APathSerializer::class)

package eu.darken.sdmse.deduplicator.core.arbiter

import androidx.annotation.Keep
import androidx.annotation.StringRes
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.serialization.APathSerializer
import eu.darken.sdmse.deduplicator.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
@JsonClassDiscriminator("criteriumType")
sealed interface ArbiterCriterium {

    fun criteriumMode(): Mode? = null

    sealed interface Mode {
        @get:StringRes val labelRes: Int
    }

    @Serializable
    @SerialName("DUPLICATE_TYPE")
    data class DuplicateType(
        @SerialName("mode") val mode: Mode = Mode.PREFER_CHECKSUM,
    ) : ArbiterCriterium {
        override fun criteriumMode(): Mode = mode

        @Serializable
        @Keep
        enum class Mode(@StringRes override val labelRes: Int) : ArbiterCriterium.Mode {
            @SerialName("PREFER_CHECKSUM") PREFER_CHECKSUM(R.string.deduplicator_arbiter_mode_prefer_checksum),
            @SerialName("PREFER_PHASH") PREFER_PHASH(R.string.deduplicator_arbiter_mode_prefer_phash),
        }
    }

    @Serializable
    @SerialName("MEDIA_PROVIDER")
    data class MediaProvider(
        @SerialName("mode") val mode: Mode = Mode.PREFER_INDEXED,
    ) : ArbiterCriterium {
        override fun criteriumMode(): Mode = mode

        @Serializable
        @Keep
        enum class Mode(@StringRes override val labelRes: Int) : ArbiterCriterium.Mode {
            @SerialName("PREFER_INDEXED") PREFER_INDEXED(R.string.deduplicator_arbiter_mode_prefer_indexed),
            @SerialName("PREFER_UNKNOWN") PREFER_UNKNOWN(R.string.deduplicator_arbiter_mode_prefer_unknown),
        }
    }

    @Serializable
    @SerialName("LOCATION")
    data class Location(
        @SerialName("mode") val mode: Mode = Mode.PREFER_PRIMARY,
    ) : ArbiterCriterium {
        override fun criteriumMode(): Mode = mode

        @Serializable
        @Keep
        enum class Mode(@StringRes override val labelRes: Int) : ArbiterCriterium.Mode {
            @SerialName("PREFER_PRIMARY") PREFER_PRIMARY(R.string.deduplicator_arbiter_mode_prefer_primary),
            @SerialName("PREFER_SECONDARY") PREFER_SECONDARY(R.string.deduplicator_arbiter_mode_prefer_secondary),
        }
    }

    @Serializable
    @SerialName("NESTING")
    data class Nesting(
        @SerialName("mode") val mode: Mode = Mode.PREFER_SHALLOW,
    ) : ArbiterCriterium {
        override fun criteriumMode(): Mode = mode

        @Serializable
        @Keep
        enum class Mode(@StringRes override val labelRes: Int) : ArbiterCriterium.Mode {
            @SerialName("PREFER_SHALLOW") PREFER_SHALLOW(R.string.deduplicator_arbiter_mode_prefer_shallow),
            @SerialName("PREFER_DEEPER") PREFER_DEEPER(R.string.deduplicator_arbiter_mode_prefer_deeper),
        }
    }

    @Serializable
    @SerialName("MODIFIED")
    data class Modified(
        @SerialName("mode") val mode: Mode = Mode.PREFER_OLDER,
    ) : ArbiterCriterium {
        override fun criteriumMode(): Mode = mode

        @Serializable
        @Keep
        enum class Mode(@StringRes override val labelRes: Int) : ArbiterCriterium.Mode {
            @SerialName("PREFER_OLDER") PREFER_OLDER(R.string.deduplicator_arbiter_mode_prefer_older),
            @SerialName("PREFER_NEWER") PREFER_NEWER(R.string.deduplicator_arbiter_mode_prefer_newer),
        }
    }

    @Serializable
    @SerialName("SIZE")
    data class Size(
        @SerialName("mode") val mode: Mode = Mode.PREFER_LARGER,
    ) : ArbiterCriterium {
        override fun criteriumMode(): Mode = mode

        @Serializable
        @Keep
        enum class Mode(@StringRes override val labelRes: Int) : ArbiterCriterium.Mode {
            @SerialName("PREFER_LARGER") PREFER_LARGER(R.string.deduplicator_arbiter_mode_prefer_larger),
            @SerialName("PREFER_SMALLER") PREFER_SMALLER(R.string.deduplicator_arbiter_mode_prefer_smaller),
        }
    }

    @Serializable
    @SerialName("PREFERRED_PATH")
    data class PreferredPath(
        @SerialName("keepPreferPaths") val keepPreferPaths: Set<APath> = emptySet(),
    ) : ArbiterCriterium

}
