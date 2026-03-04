package eu.darken.sdmse.appcontrol.core.usage

import android.app.usage.UsageStats
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.features.InstallId
import java.time.Duration
import java.time.Instant

data class UsageInfo(
    val installId: InstallId,
    val stats: List<UsageStats>,
) {
    val screenTime: Duration
        get() = Duration.ofMillis(
            @Suppress("NewApi")
            stats.sumOf { if (hasApiLevel(29)) it.totalTimeVisible else it.totalTimeInForeground }
        )

    val screenTimeSince: Instant
        get() = Instant.ofEpochMilli(stats.minOf { it.firstTimeStamp })
}
