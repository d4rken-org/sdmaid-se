package eu.darken.sdmse.common.upgrade.core

import eu.darken.sdmse.common.serialization.InstantSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class FossUpgrade(
    @SerialName("upgradedAt") @Serializable(with = InstantSerializer::class) val upgradedAt: Instant,
    @SerialName("upgradeType") val upgradeType: Type,
) {
    @Serializable
    enum class Type {
        @SerialName("GITHUB_SPONSORS") GITHUB_SPONSORS,
        ;
    }
}
