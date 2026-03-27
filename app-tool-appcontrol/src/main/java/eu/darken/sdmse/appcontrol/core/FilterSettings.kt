package eu.darken.sdmse.appcontrol.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FilterSettings(
    @SerialName("tags") val tags: Set<Tag> = setOf(
        Tag.USER,
        Tag.ENABLED,
    ),
) {
    @Serializable
    enum class Tag {
        @SerialName("USER") USER,
        @SerialName("SYSTEM") SYSTEM,
        @SerialName("ENABLED") ENABLED,
        @SerialName("DISABLED") DISABLED,
        @SerialName("ACTIVE") ACTIVE,
        @SerialName("NOT_INSTALLED") NOT_INSTALLED,
        ;
    }
}
