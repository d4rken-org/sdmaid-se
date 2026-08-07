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
 * The one-tap-actionable cleanup summary surfaced by the hero card. [tools] (and with it
 * [totalSize]/[itemCount]) reflects exactly what the main action
 * ([DashboardMainActionEngine.mainAction] with [BottomBarState.Action.DELETE]) will free: each tool
 * is included only when its one-click toggle is enabled, it has data, and — for AppCleaner and
 * Deduplicator — the user is Pro. Deduplicator contributes its freeable redundant size and the count
 * of redundant files a keep-one delete removes, so [itemCount] is a uniform discrete-file count
 * across all tools.
 *
 * Findings a non-Pro user cannot act on are not dropped, they move to [lockedTools] — the card shows
 * them as an upsell, kept strictly apart from the amounts the main action can actually deliver.
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
    /**
     * Findings a Pro-gated tool holds that this user cannot act on. Disjoint from [tools]: a tool is
     * in one list or the other, never both.
     */
    val lockedTools: List<ToolSlice> = emptyList(),
) {
    /** What Pro would additionally unlock. Not part of [totalSize]/[itemCount] — see [lockedTools]. */
    val lockedSize: Long get() = lockedTools.sumOf { it.size }
    val lockedCount: Int get() = lockedTools.sumOf { it.count }

    /**
     * FREEABLE = "X will be freed" (post-scan); FREED = "X freed" (post-delete/one-click);
     * NOTHING_FREED = a cleanup ran but freed nothing, which carries no amounts and no [tools];
     * LOCKED_ONLY = every finding is Pro-gated, so [tools] is empty and [totalSize]/[itemCount] stay
     * 0 — they mean "what the main action will free" everywhere else and must keep meaning that.
     */
    enum class Mode { FREEABLE, FREED, NOTHING_FREED, LOCKED_ONLY }

    data class ToolSlice(
        val type: SDMTool.Type,
        val size: Long,
        /** Discrete removable-file count: scan items for CorpseFinder/SystemCleaner/AppCleaner; redundant files for Deduplicator. */
        val count: Int,
    )
}

/**
 * Whether the hero renders its nested upgrade block: only when free and Pro-gated findings coexist,
 * so the block has something to be "additional" to. With no free chips the block would be the card's
 * whole content, and those states stay flat (locked chips in the main row).
 *
 * Single source of truth on purpose — the card's render branch and the dock's height reservation
 * both read this. If they ever disagreed, the card would clip or the dock would reserve dead space,
 * and neither shows up in a unit test.
 */
internal val HeroSummary.showsUpgradeBlock: Boolean
    get() = tools.isNotEmpty() && lockedTools.isNotEmpty()

data class OneClickOptionsState(
    val corpseFinderEnabled: Boolean = true,
    val systemCleanerEnabled: Boolean = true,
    val appCleanerEnabled: Boolean = true,
    val deduplicatorEnabled: Boolean = false,
)
