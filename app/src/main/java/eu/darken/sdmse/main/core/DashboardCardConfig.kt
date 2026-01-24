package eu.darken.sdmse.main.core

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DashboardCardConfig(
    @Json(name = "cards") val cards: List<CardEntry> = defaultCards,
) {
    @JsonClass(generateAdapter = true)
    data class CardEntry(
        @Json(name = "type") val type: DashboardCardType,
        @Json(name = "isVisible") val isVisible: Boolean = true,
    )

    companion object {
        val defaultCards: List<CardEntry> = DashboardCardType.entries.map { CardEntry(it) }
    }
}
