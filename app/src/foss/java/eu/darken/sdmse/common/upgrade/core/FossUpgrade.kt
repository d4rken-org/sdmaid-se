package eu.darken.sdmse.common.upgrade.core

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.Instant

@JsonClass(generateAdapter = true)
data class FossUpgrade(
    @Json(name = "upgradedAt") val upgradedAt: Instant,
    @Json(name = "upgradeType") val upgradeType: Type,
) {
    @JsonClass(generateAdapter = false)
    enum class Type {
        @Json(name = "GITHUB_SPONSORS") GITHUB_SPONSORS,
        ;
    }
}