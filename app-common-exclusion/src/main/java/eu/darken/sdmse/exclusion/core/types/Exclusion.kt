package eu.darken.sdmse.exclusion.core.types

import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.files.APath
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable(with = ExclusionSerializer::class)
sealed interface Exclusion {
    val id: ExclusionId

    val tags: Set<Tag>

    val label: CaString

    @Serializable
    enum class Tag {
        @SerialName("GENERAL") GENERAL,
        @SerialName("CORPSEFINDER") CORPSEFINDER,
        @SerialName("SYSTEMCLEANER") SYSTEMCLEANER,
        @SerialName("APPCLEANER") APPCLEANER,
        @SerialName("DEDUPLICATOR") DEDUPLICATOR,
        @SerialName("SQUEEZER") SQUEEZER,
        @SerialName("SWIPER") SWIPER,
    }

    interface Pkg : Exclusion {
        suspend fun match(candidate: eu.darken.sdmse.common.pkgs.Pkg.Id): Boolean
    }

    interface Path : Exclusion {
        suspend fun match(candidate: APath): Boolean
    }

}
