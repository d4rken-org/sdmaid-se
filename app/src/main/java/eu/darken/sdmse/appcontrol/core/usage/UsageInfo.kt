package eu.darken.sdmse.appcontrol.core.usage

import android.app.usage.UsageStats
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.features.Installed
import java.time.Duration

data class UsageInfo(
    val installId: Installed.InstallId,
    val stat: UsageStats,
) {
    val screenTime: Duration by lazy {
        Duration.ofMillis(
            @Suppress("NewApi")
            if (hasApiLevel(29)) stat.totalTimeVisible else stat.totalTimeInForeground
        )
    }
}
