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
 * redundant size and the count of redundant files a keep-one delete removes, so [itemCount] is a
 * uniform discrete-file count across all tools.
 */
data class HeroSummary(
    val mode: Mode,
    val totalSize: Long,
    val itemCount: Int,
    val tools: List<ToolSlice>,
    /** When the displayed data came to be: latest scan of the included tools (FREEABLE) or deletion end (FREED). */
    val timestamp: Instant? = null,
    /**
     * What the cleanup left behind, across the tools it actually submitted to. 0 unless [mode] is
     * [Mode.FREED].
     *
     * Deliberately reported as "left", not "couldn't be freed": leftovers usually *are* junk that
     * resisted deletion (a locked system app's cache fails on every run), but the tools don't record
     * which items were attempted and failed versus never attempted at all — AppCleaner skips
     * inaccessible caches outright when that option is off. Naming a cause would assert something
     * only AppCleaner can currently substantiate.
     */
    val residueSize: Long = 0L,
    val residueCount: Int = 0,
) {
    /**
     * FREEABLE = "X will be freed" (post-scan); FREED = "X freed" (post-delete/one-click);
     * NOTHING_FREED = a cleanup ran but freed nothing, which carries no amounts and no [tools].
     */
    enum class Mode { FREEABLE, FREED, NOTHING_FREED }

    data class ToolSlice(
        val type: SDMTool.Type,
        val size: Long,
        /** Discrete removable-file count: scan items for CorpseFinder/SystemCleaner/AppCleaner; redundant files for Deduplicator. */
        val count: Int,
    )
}

data class OneClickOptionsState(
    val corpseFinderEnabled: Boolean = true,
    val systemCleanerEnabled: Boolean = true,
    val appCleanerEnabled: Boolean = true,
    val deduplicatorEnabled: Boolean = false,
)
