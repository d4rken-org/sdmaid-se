package eu.darken.sdmse.main.ui.dashboard

import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.main.core.SDMTool
import java.time.Instant

data class BottomBarState(
    val isReady: Boolean,
    val actionState: Action,
    val activeTasks: Int,
    val queuedTasks: Int,
    val heroSummary: HeroSummary?,
    val upgradeInfo: UpgradeRepo.Info?,
    /** Minute-ticked wall clock so the hero's relative timestamp doesn't go stale on screen. */
    val now: Instant = Instant.EPOCH,
) {
    enum class Action {
        SCAN,
        DELETE,
        ONECLICK,
        WORKING,
        WORKING_CANCELABLE
    }
}

/**
 * The one-tap-actionable cleanup summary surfaced by the hero card. Reflects exactly what the
 * main action ([DashboardMainActionEngine.mainAction] with [BottomBarState.Action.DELETE]) will
 * free: each tool is included only when its one-click toggle is enabled, it has data, and — for
 * AppCleaner and Deduplicator — the user is Pro. Deduplicator contributes its freeable
 * redundant size and a cluster count (kept out of [itemCount], which counts discrete files only).
 */
data class HeroSummary(
    val mode: Mode,
    val totalSize: Long,
    val itemCount: Int,
    val tools: List<ToolSlice>,
    /** When the displayed data came to be: latest scan of the included tools (FREEABLE) or deletion end (FREED). */
    val timestamp: Instant? = null,
) {
    /** FREEABLE = "X will be freed" (post-scan); FREED = "X freed" (post-delete/one-click). */
    enum class Mode { FREEABLE, FREED }

    data class ToolSlice(
        val type: SDMTool.Type,
        val size: Long,
        /** Discrete file count for CorpseFinder/SystemCleaner/AppCleaner; cluster count for Deduplicator. */
        val count: Int,
    )
}

data class OneClickOptionsState(
    val corpseFinderEnabled: Boolean = true,
    val systemCleanerEnabled: Boolean = true,
    val appCleanerEnabled: Boolean = true,
    val deduplicatorEnabled: Boolean = false,
)
