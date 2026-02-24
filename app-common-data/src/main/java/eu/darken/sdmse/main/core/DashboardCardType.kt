package eu.darken.sdmse.main.core

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
enum class DashboardCardType {
    CORPSEFINDER,
    SYSTEMCLEANER,
    APPCLEANER,
    DEDUPLICATOR,
    APPCONTROL,
    ANALYZER,
    SWIPER,
    SQUEEZER,
    SCHEDULER,
    STATS,
}
