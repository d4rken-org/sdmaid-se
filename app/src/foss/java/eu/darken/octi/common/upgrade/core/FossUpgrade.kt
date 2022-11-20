package eu.darken.sdmse.common.upgrade.core

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.Instant

@JsonClass(generateAdapter = true)
data class FossUpgrade(
    val upgradedAt: Instant,
    val reason: Reason
) {
    @JsonClass(generateAdapter = false)
    enum class Reason {
        @Json(name = "foss.upgrade.reason.donated") DONATED,
        @Json(name = "foss.upgrade.reason.alreadydonated") ALREADY_DONATED,
        @Json(name = "foss.upgrade.reason.nomoney") NO_MONEY;
    }
}