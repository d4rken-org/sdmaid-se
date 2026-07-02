package eu.darken.sdmse.main.ui.dashboard

import eu.darken.sdmse.appcleaner.core.AppCleaner
import eu.darken.sdmse.appcleaner.core.hasData
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerOneClickTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerProcessingTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerScanTask
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.intervalFlow
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.isProForUi
import eu.darken.sdmse.corpsefinder.core.CorpseFinder
import eu.darken.sdmse.corpsefinder.core.hasData
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderDeleteTask
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderOneClickTask
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderScanTask
import eu.darken.sdmse.deduplicator.core.Deduplicator
import eu.darken.sdmse.deduplicator.core.hasData
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorDeleteTask
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorOneClickTask
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorScanTask
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import eu.darken.sdmse.stats.core.ReportDetails
import eu.darken.sdmse.systemcleaner.core.SystemCleaner
import eu.darken.sdmse.systemcleaner.core.hasData
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerOneClickTask
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerProcessingTask
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerScanTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

/**
 * The dashboard's main-action/hero state machine: resolves what the main button does, runs its
 * per-tool branches, tracks freed results, and assembles the bottom bar state. Extracted from
 * [DashboardViewModel], which delegates to it and owns error routing and navigation.
 *
 * [scope] must be a ViewModel-style supervised scope (vmScope): branches and the internal
 * collectors are launched on it, and a failing child must not cancel its siblings.
 */
class DashboardMainActionEngine(
    private val scope: CoroutineScope,
    private val taskManager: TaskManager,
    private val corpseFinder: CorpseFinder,
    private val systemCleaner: SystemCleaner,
    private val appCleaner: AppCleaner,
    private val deduplicator: Deduplicator,
    private val generalSettings: GeneralSettings,
    private val upgradeRepo: UpgradeRepo,
    /** The VM's shared upgrade flow (immediate null first value) — NOT the raw repo flow. */
    private val upgradeInfo: Flow<UpgradeRepo.Info?>,
    /** Submits via the VM so task results keep routing to its one-shot events. */
    private val submitTask: suspend (SDMTool.Task) -> SDMTool.Task.Result,
    /** Invoked when a non-Pro user triggers a Pro-gated cleanup; the VM navigates to upgrade. */
    private val onUpgradeRequired: () -> Unit,
) {

    /** Cold per-setting combine; the VM turns this into the shared [OneClickOptionsState] StateFlow. */
    val oneClickOptions: Flow<OneClickOptionsState> = combine(
        generalSettings.oneClickCorpseFinderEnabled.flow,
        generalSettings.oneClickSystemCleanerEnabled.flow,
        generalSettings.oneClickAppCleanerEnabled.flow,
        generalSettings.oneClickDeduplicatorEnabled.flow,
    ) { corpseFinderEnabled, systemCleanerEnabled, appCleanerEnabled, deduplicatorEnabled ->
        OneClickOptionsState(
            corpseFinderEnabled = corpseFinderEnabled,
            systemCleanerEnabled = systemCleanerEnabled,
            appCleanerEnabled = appCleanerEnabled,
            deduplicatorEnabled = deduplicatorEnabled,
        )
    }

    /** Aggregated "freed" result of the most recent main-action deletion/one-click; null otherwise. */
    private val freedResult = MutableStateFlow<HeroSummary?>(null)

    /**
     * Batch start of the current cleanup, stamped in [mainAction] before any branch runs. Used to
     * resolve each freed-hero chip to *this* cleanup's per-tool report (reports completed at/after
     * this instant), instead of a stale earlier one. [Instant.EPOCH] until the first cleanup.
     */
    var freedResultSince: Instant = Instant.EPOCH
        private set

    /** In-flight main-action cleanup branches; the freed hero stays hidden until this reaches 0. */
    private val pendingMainCleanup = MutableStateFlow(0)

    /** While [discardResults] clears the tools one by one, suppress the hero so it never shows partial data. */
    private val discarding = MutableStateFlow(false)

    private val nowTicks: Flow<Instant> = intervalFlow(1.minutes).map { Instant.now() }

    private val heroDismissed = MutableStateFlow(false)

    /** Whether the user dismissed the hero for the current results. In-memory; resets on a fresh scan. */
    val isHeroDismissed: StateFlow<Boolean> = heroDismissed

    init {
        // A freshly completed *scan* clears any stale "freed" result and revives a dismissed hero.
        // We must only react to a *strictly newer* scan time: TaskManager keeps one task per tool,
        // so a delete prunes that tool's scan result and would otherwise make this "change" to an
        // older/absent scan time and wrongly clear the freed hero we just produced.
        scope.launch {
            var latestSeenScan: Instant? = null
            taskManager.state
                .mapNotNull { state -> state.latestScanTimes().values.maxOrNull() }
                .collect { scanCompletedAt ->
                    val prev = latestSeenScan
                    if (prev == null || scanCompletedAt.isAfter(prev)) {
                        latestSeenScan = scanCompletedAt
                        freedResult.value = null
                        heroDismissed.value = false
                    }
                }
        }
        // A freshly produced "freed" result also revives a dismissed hero so the outcome is shown.
        scope.launch {
            freedResult
                .map { it != null }
                .distinctUntilChanged()
                .collect { hasFreed -> if (hasFreed) heroDismissed.value = false }
        }
    }

    /**
     * Cold bottom bar state assembly. [listIsReady] and [oneClickOptionsState] are the VM's shared
     * flows (list readiness derives from its listState; the options StateFlow is built from
     * [oneClickOptions]) so their upstreams aren't collected twice.
     */
    fun bottomBarState(
        listIsReady: Flow<Boolean>,
        oneClickOptionsState: Flow<OneClickOptionsState>,
    ): Flow<BottomBarState> = eu.darken.sdmse.common.flow.combine(
        upgradeInfo,
        taskManager.state,
        corpseFinder.state,
        systemCleaner.state,
        appCleaner.state,
        deduplicator.state,
        generalSettings.enableDashboardOneClick.flow,
        oneClickOptionsState,
        freedResult,
        pendingMainCleanup,
        listIsReady,
        discarding,
        nowTicks,
    ) { upgradeInfo,
        taskState,
        corpseState,
        filterState,
        junkState,
        dedupeState,
        oneClickMode,
        oneClickOptions,
        freed,
        pendingCleanup,
        listReady,
        isDiscarding,
        now ->

        val actionState = resolveMainAction(
            taskState = taskState,
            corpse = corpseState.data,
            system = filterState.data,
            app = junkState.data,
            dedupe = dedupeState.data,
            oneClick = oneClickOptions,
            isPro = upgradeInfo?.isPro == true,
            oneClickMode = oneClickMode,
        )
        val activeTasks = taskState.tasks.filter { it.isActive }.size
        val queuedTasks = taskState.tasks.filter { it.isQueued }.size
        // Post-scan "will be freed" takes priority; otherwise, once the action has settled, show the
        // "freed" result of the last main-action deletion/one-click. While working, both stay hidden
        // and the bar carries progress.
        val freeable = if (actionState == BottomBarState.Action.DELETE) {
            buildHeroSummary(
                corpse = corpseState.data,
                system = filterState.data,
                app = junkState.data,
                dedupe = dedupeState.data,
                oneClick = oneClickOptions,
                isPro = upgradeInfo?.isPro == true,
                scanTimes = taskState.latestScanTimes(),
            )
        } else {
            null
        }
        val heroSummary = (freeable ?: freed?.takeIf { taskState.isIdle && pendingCleanup == 0 })
            .takeIf { !isDiscarding }
        BottomBarState(
            isReady = listReady,
            actionState = actionState,
            activeTasks = activeTasks,
            queuedTasks = queuedTasks,
            heroSummary = heroSummary,
            upgradeInfo = upgradeInfo,
            now = now,
        )
    }

    fun dismissHero() {
        log(TAG) { "dismissHero()" }
        heroDismissed.value = true
    }

    /** Re-shows a hero the user dismissed (via the compact summary chip in the bar). */
    fun restoreHero() {
        log(TAG) { "restoreHero()" }
        heroDismissed.value = false
    }

    /**
     * Drops all pending scan results, returning the dashboard to its pristine SCAN state. Unlike
     * [dismissHero] (which only hides the card), this clears the tools' data, so the main action
     * no longer threatens deletion. Recoverable by simply rescanning, hence no confirmation step.
     */
    fun discardResults() = scope.launch {
        log(TAG, INFO) { "discardResults()" }
        if (!taskManager.state.first().isIdle) {
            // A task snuck in between the button click and us; don't queue up behind the tool
            // locks just to wipe results the user hasn't even seen yet.
            log(TAG, WARN) { "discardResults(): tasks are running, aborting" }
            return@launch
        }
        discarding.value = true
        try {
            freedResult.value = null
            corpseFinder.discardScanData()
            systemCleaner.discardScanData()
            appCleaner.discardScanData()
            deduplicator.discardScanData()
            // The dashboard tool cards show the last *task result*, not the tools' data; forget
            // those too or the cards keep advertising freeable space that no longer exists.
            taskManager.forgetCompleted(SDMTool.Type.CORPSEFINDER)
            taskManager.forgetCompleted(SDMTool.Type.SYSTEMCLEANER)
            taskManager.forgetCompleted(SDMTool.Type.APPCLEANER)
            taskManager.forgetCompleted(SDMTool.Type.DEDUPLICATOR)
            heroDismissed.value = false
        } finally {
            discarding.value = false
        }
    }

    fun setCorpseFinderOneClickEnabled(enabled: Boolean) = scope.launch {
        generalSettings.oneClickCorpseFinderEnabled.value(enabled)
    }

    fun setSystemCleanerOneClickEnabled(enabled: Boolean) = scope.launch {
        generalSettings.oneClickSystemCleanerEnabled.value(enabled)
    }

    fun setAppCleanerOneClickEnabled(enabled: Boolean) = scope.launch {
        generalSettings.oneClickAppCleanerEnabled.value(enabled)
    }

    fun setDeduplicatorOneClickEnabled(enabled: Boolean) = scope.launch {
        generalSettings.oneClickDeduplicatorEnabled.value(enabled)
    }

    // Runs one main-action tool branch and, for cleanups, decrements the pending counter when it
    // settles — so the freed hero only appears once *all* branches are done (no partial flash).
    private fun launchMainBranch(isCleanup: Boolean, block: suspend () -> Unit) = scope.launch {
        try {
            block()
        } finally {
            if (isCleanup) pendingMainCleanup.update { (it - 1).coerceAtLeast(0) }
        }
    }

    fun mainAction(actionState: BottomBarState.Action) {
        log(TAG) { "mainAction(actionState=$actionState)" }
        // Start a fresh "freed" tally for this deletion/one-click. The hero stays hidden until every
        // branch has settled (pendingMainCleanup == 0) so a partial per-tool result can't flash.
        val isCleanup = actionState == BottomBarState.Action.DELETE || actionState == BottomBarState.Action.ONECLICK
        if (isCleanup) {
            freedResult.value = null
            // Stamp before any branch runs so it's <= every resulting report's end_at.
            freedResultSince = Instant.now()
            pendingMainCleanup.value = 4 // CorpseFinder + SystemCleaner + AppCleaner + Deduplicator
        }
        launchMainBranch(isCleanup) {
            if (!generalSettings.oneClickCorpseFinderEnabled.value()) {
                log(VERBOSE) { "CorpseFinder is disabled one-click mode." }
                return@launchMainBranch
            }

            when (actionState) {
                BottomBarState.Action.SCAN -> submitTask(CorpseFinderScanTask())
                BottomBarState.Action.WORKING_CANCELABLE -> taskManager.cancel(SDMTool.Type.CORPSEFINDER)
                BottomBarState.Action.WORKING -> {}
                BottomBarState.Action.DELETE -> if (corpseFinder.state.first().data != null) {
                    accumulateFreed(SDMTool.Type.CORPSEFINDER, submitTask(CorpseFinderDeleteTask()))
                }

                BottomBarState.Action.ONECLICK -> accumulateFreed(
                    SDMTool.Type.CORPSEFINDER,
                    submitTask(CorpseFinderOneClickTask()),
                )
            }
        }
        launchMainBranch(isCleanup) {
            if (!generalSettings.oneClickSystemCleanerEnabled.value()) {
                log(VERBOSE) { "SystemCleaner is disabled one-click mode." }
                return@launchMainBranch
            }

            when (actionState) {
                BottomBarState.Action.SCAN -> submitTask(SystemCleanerScanTask())
                BottomBarState.Action.WORKING_CANCELABLE -> taskManager.cancel(SDMTool.Type.SYSTEMCLEANER)
                BottomBarState.Action.WORKING -> {}
                BottomBarState.Action.DELETE -> if (systemCleaner.state.first().data != null) {
                    accumulateFreed(SDMTool.Type.SYSTEMCLEANER, submitTask(SystemCleanerProcessingTask()))
                }

                BottomBarState.Action.ONECLICK -> accumulateFreed(
                    SDMTool.Type.SYSTEMCLEANER,
                    submitTask(SystemCleanerOneClickTask()),
                )
            }
        }
        launchMainBranch(isCleanup) {
            if (!generalSettings.oneClickAppCleanerEnabled.value()) {
                log(VERBOSE) { "AppCleaner is disabled one-click mode." }
                return@launchMainBranch
            }

            when (actionState) {
                BottomBarState.Action.SCAN -> submitTask(AppCleanerScanTask())
                BottomBarState.Action.WORKING_CANCELABLE -> taskManager.cancel(SDMTool.Type.APPCLEANER)
                BottomBarState.Action.WORKING -> {}
                BottomBarState.Action.DELETE -> {
                    if (appCleaner.state.first().data != null && upgradeRepo.isProForUi()) {
                        accumulateFreed(SDMTool.Type.APPCLEANER, submitTask(AppCleanerProcessingTask()))
                    } else if (appCleaner.state.first().data.hasData && !corpseFinder.state.first().data.hasData && !systemCleaner.state.first().data.hasData) {
                        onUpgradeRequired()
                    }
                }

                BottomBarState.Action.ONECLICK -> {
                    if (upgradeRepo.isProForUi()) {
                        accumulateFreed(SDMTool.Type.APPCLEANER, submitTask(AppCleanerOneClickTask()))
                    } else if (appCleaner.state.first().data.hasData && !corpseFinder.state.first().data.hasData && !systemCleaner.state.first().data.hasData) {
                        onUpgradeRequired()
                    }
                }
            }
        }
        launchMainBranch(isCleanup) {
            if (!generalSettings.oneClickDeduplicatorEnabled.value()) {
                log(VERBOSE) { "Deduplicator is disabled one-click mode." }
                return@launchMainBranch
            }

            when (actionState) {
                BottomBarState.Action.SCAN -> submitTask(DeduplicatorScanTask())
                BottomBarState.Action.WORKING_CANCELABLE -> taskManager.cancel(SDMTool.Type.DEDUPLICATOR)
                BottomBarState.Action.WORKING -> {}
                BottomBarState.Action.DELETE -> if (deduplicator.state.first().data != null && upgradeRepo.isProForUi()) {
                    accumulateFreed(SDMTool.Type.DEDUPLICATOR, submitTask(DeduplicatorDeleteTask()))
                }

                BottomBarState.Action.ONECLICK -> {
                    if (upgradeRepo.isProForUi()) {
                        accumulateFreed(SDMTool.Type.DEDUPLICATOR, submitTask(DeduplicatorOneClickTask()))
                    } else if (deduplicator.state.first().data.hasData && !corpseFinder.state.first().data.hasData && !systemCleaner.state.first().data.hasData) {
                        onUpgradeRequired()
                    }
                }
            }
        }
    }

    /** Folds a deletion/one-click result into [freedResult] so the hero can show what was freed. */
    private fun accumulateFreed(type: SDMTool.Type, result: SDMTool.Task.Result) {
        val space = (result as? ReportDetails.AffectedSpace)?.affectedSpace ?: 0L
        val count = (result as? ReportDetails.AffectedCount)?.affectedCount ?: 0
        if (space <= 0L && count <= 0) return
        freedResult.update { current ->
            val slices = (current?.tools.orEmpty()).filterNot { it.type == type } +
                HeroSummary.ToolSlice(type, space, count)
            HeroSummary(
                mode = HeroSummary.Mode.FREED,
                totalSize = slices.sumOf { it.size },
                itemCount = slices.sumOf { it.count },
                tools = slices,
                timestamp = Instant.now(),
            )
        }
    }

    companion object {
        private val TAG = logTag("Dashboard", "MainActionEngine")

        /**
         * Resolves what the main dashboard button does. CorpseFinder/SystemCleaner/AppCleaner data
         * arms DELETE unconditionally (AppCleaner upsells non-Pro users instead of deleting).
         * Deduplicator arms DELETE only when it is opted into one-click AND the user is Pro —
         * exactly the conditions under which [mainAction]'s DELETE branch will actually submit a
         * deletion task for it; its primary delete flow remains in-tool cluster selection.
         */
        internal fun resolveMainAction(
            taskState: TaskSubmitter.State,
            corpse: CorpseFinder.Data?,
            system: SystemCleaner.Data?,
            app: AppCleaner.Data?,
            dedupe: Deduplicator.Data?,
            oneClick: OneClickOptionsState,
            isPro: Boolean,
            oneClickMode: Boolean,
        ): BottomBarState.Action = when {
            taskState.hasCancellable -> BottomBarState.Action.WORKING_CANCELABLE
            !taskState.isIdle -> BottomBarState.Action.WORKING
            corpse.hasData || system.hasData || app.hasData -> BottomBarState.Action.DELETE
            dedupe.hasData && oneClick.deduplicatorEnabled && isPro -> BottomBarState.Action.DELETE
            oneClickMode -> BottomBarState.Action.ONECLICK
            else -> BottomBarState.Action.SCAN
        }

        /**
         * Builds the action-truthful hero summary: only tools the main DELETE action will actually
         * free (one-click toggle on, has data, AppCleaner and Deduplicator additionally require
         * Pro). Returns null when nothing is one-tap-actionable for this user/config, even if raw
         * scan data exists.
         */
        internal fun buildHeroSummary(
            corpse: CorpseFinder.Data?,
            system: SystemCleaner.Data?,
            app: AppCleaner.Data?,
            dedupe: Deduplicator.Data?,
            oneClick: OneClickOptionsState,
            isPro: Boolean,
            scanTimes: Map<SDMTool.Type, Instant> = emptyMap(),
        ): HeroSummary? {
            val tools = buildList {
                corpse?.takeIf { oneClick.corpseFinderEnabled && it.hasData }?.let {
                    add(HeroSummary.ToolSlice(SDMTool.Type.CORPSEFINDER, it.totalSize, it.totalCount))
                }
                system?.takeIf { oneClick.systemCleanerEnabled && it.hasData }?.let {
                    add(HeroSummary.ToolSlice(SDMTool.Type.SYSTEMCLEANER, it.totalSize, it.totalCount))
                }
                app?.takeIf { oneClick.appCleanerEnabled && isPro && it.hasData }?.let {
                    add(HeroSummary.ToolSlice(SDMTool.Type.APPCLEANER, it.totalSize, it.totalCount))
                }
                dedupe?.takeIf { oneClick.deduplicatorEnabled && isPro && it.hasData }?.let {
                    add(HeroSummary.ToolSlice(SDMTool.Type.DEDUPLICATOR, it.redundantSize, it.redundantCount))
                }
            }
            if (tools.isEmpty()) return null
            return HeroSummary(
                mode = HeroSummary.Mode.FREEABLE,
                totalSize = tools.sumOf { it.size },
                // Every included tool contributes a discrete removable-file count (Deduplicator: the
                // redundant files a keep-one delete removes) — so the headline is a true file count.
                itemCount = tools.sumOf { it.count },
                tools = tools,
                // Only the *included* tools' scans: a newer scan of an absent tool must not make
                // this summary's data look fresher than it is.
                timestamp = tools.mapNotNull { scanTimes[it.type] }.maxOrNull(),
            )
        }
    }
}

/**
 * Latest successful scan completion per tool, considering only the hero/main-action scan results.
 * Single source of truth for "what counts as a dashboard scan" — used both to revive a dismissed
 * hero on fresh scans and to stamp [HeroSummary.timestamp].
 */
internal fun TaskSubmitter.State.latestScanTimes(): Map<SDMTool.Type, Instant> = tasks
    .filter { task ->
        task.isComplete && when (task.result) {
            is CorpseFinderScanTask.Success,
            is SystemCleanerScanTask.Success,
            is AppCleanerScanTask.Success,
            is DeduplicatorScanTask.Success -> true
            else -> false
        }
    }
    .groupBy { it.toolType }
    .mapValues { (_, tasks) -> tasks.maxOf { it.completedAt!! } }
