package eu.darken.sdmse.main.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DashboardCardConfig(
    @SerialName("cards") val cards: List<CardEntry> = defaultCards,
) {
    @Serializable
    data class CardEntry(
        @SerialName("type") val type: DashboardCardType,
        @SerialName("isVisible") val isVisible: Boolean = true,
    )

    companion object {
        val defaultCards: List<CardEntry> = DashboardCardType.entries.map { CardEntry(it) }
    }
}
