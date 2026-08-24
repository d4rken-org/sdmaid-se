package eu.darken.sdmse.main.ui.dashboard

import eu.darken.sdmse.appcleaner.core.AppCleaner
import eu.darken.sdmse.appcleaner.core.hasActionableData
import eu.darken.sdmse.appcleaner.core.hasData
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerOneClickTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerProcessingTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerScanTask
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
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
import eu.darken.sdmse.main.core.taskmanager.getLatestTask
import eu.darken.sdmse.stats.core.ReportDetails
import eu.darken.sdmse.systemcleaner.core.SystemCleaner
import eu.darken.sdmse.systemcleaner.core.hasData
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerOneClickTask
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerProcessingTask
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerScanTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
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
    /**
     * Invoked when a state flow this engine owns fails and falls back. The VM routes it to
     * `errorEvents` — without this the user would silently get fallback state (e.g. default
     * one-click toggles) with no indication that their settings couldn't be read.
     */
    private val onStateError: (Throwable) -> Unit,
) {

    /** Cold per-setting combine; [oneClickOptionsState] is the single shared collection of it. */
    private val oneClickOptions: Flow<OneClickOptionsState> = combine(
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

    /**
     * The one-click toggles, shared: the several [heroState] readers take this single collection
     * instead of subscribing to the four settings flows each time. Upstream failures fall back to
     * the defaults rather than freezing the dashboard.
     */
    private val oneClickOptionsState: StateFlow<OneClickOptionsState> = oneClickOptions
        .catch { e ->
            if (e is CancellationException) throw e
            log(TAG, ERROR) { "oneClickOptions failed: ${e.asLog()}" }
            onStateError(e)
            emit(OneClickOptionsState())
        }
        .stateIn(scope, SharingStarted.Eagerly, OneClickOptionsState())

    /** Aggregated "freed" result of the most recent main-action deletion/one-click; null otherwise. */
    private val freedResult = MutableStateFlow<HeroSummary?>(null)

    /**
     * Batch start of the current cleanup, stamped in [mainAction] before any branch runs. Used to
     * resolve each freed-hero chip to *this* cleanup's per-tool report (reports completed at/after
     * this instant), instead of a stale earlier one. [Instant.EPOCH] until the first cleanup.
     */
    var freedResultSince: Instant = Instant.EPOCH
        private set

    /**
     * Branch bookkeeping for the current main action, deliberately NOT observed by anything. Its
     * only job is to tell the last returning branch that it *is* the last one, while it still has
     * results to write; [batchState] is published afterwards. See [launchMainBranch].
     */
    private val branchesInFlight = AtomicInteger(0)

    /** The task instance each branch of the current run submitted, accumulated as they go. */
    private val submittedTasks = MutableStateFlow(emptyMap<SDMTool.Type, SDMTool.Task>())

    /** Latest completed task instance on record per cleanup tool; the baseline a settle snapshots. */
    @Volatile
    private var tasksOnRecord: Map<SDMTool.Type, SDMTool.Task> = emptyMap()

    /**
     * Which task executions produced [freedResult], published once by the last branch of a cleanup.
     *
     * The task manager keeps exactly one completed entry per tool and hands back the very [SDMTool.Task]
     * instance it was given, so referential identity answers "is the result currently on record for this
     * tool still mine?" without a shared clock, an id plumbed through the task manager's API, or a
     * snapshot taken at a moment that may not have settled. That matters because every timestamp-based
     * answer to this question is wrong somewhere: wall clocks move, the tools' `replayingShare` states
     * may not have published by the settle edge, and the task manager runs work this engine never
     * submitted (a tool's own delete screen, the scheduler, the one-tap shortcut).
     *
     * Null whenever no cleanup outcome is being vouched for, which makes the check fail closed: an
     * unvouched freed result is simply not shown, and the hero falls back to live data.
     *
     * Per cleanup tool this holds the instances that may legitimately be on record afterwards: the one
     * the cleanup submitted, and the one already there when it settled. The latter covers two cases at
     * once — a tool the cleanup never touched (its record must stay put, so an in-tool delete there is
     * still caught) and a tool whose own result has not been published yet (the previous record is
     * still acceptable, so a cleanup is never briefly judged stale on its own work).
     */
    @Volatile
    private var freedProvenance: FreedProvenance? = null

    /**
     * [acceptable] spans every cleanup tool, including ones this run never touched — their record
     * must stay put too, or an in-tool delete there would go unnoticed. [submitted] is the narrower
     * set the run actually ran, which is what leftovers are counted against.
     */
    internal data class FreedProvenance(
        val acceptable: Map<SDMTool.Type, List<SDMTool.Task>>,
        val submitted: Set<SDMTool.Type>,
    )

    /**
     * The current main-action batch, published as one atomic value: a settle edge must never become
     * visible apart from the [id] and the arm it belongs to.
     *
     * [id] identifies the run. A resolution derived from batch A can still be in flight when batch B
     * starts, and both arms are the same indistinguishable `true` — the id is what stops A's late
     * resolution from consuming B's arm.
     */
    internal data class BatchState(
        val id: Long = 0L,
        /** In-flight branches; gates the freed hero and the settle edge [heroState] carries. */
        val pending: Int = 0,
        /** A one-tap main action is running and its hero, if it produces one, should auto-expand. */
        val autoExpandArmed: Boolean = false,
    )

    /** What the settle collector does with one hero snapshot; see [resolveAutoExpand]. */
    internal enum class AutoExpandDecision {
        IGNORE,
        DISARM,
        EXPAND_AND_DISARM,
    }

    private val batchState = MutableStateFlow(BatchState())

    /** The part of [BatchState] the hero derivation keys on; see [heroState]. */
    private data class BatchPhase(val id: Long, val isSettled: Boolean)

    /**
     * Whether the current cleanup actually submitted a deletion task, regardless of how much it
     * freed. Distinguishes "ran and freed nothing" (worth telling the user about) from "every
     * branch opted out or upsold instead", which produces no result by design.
     */
    private val cleanupRan = MutableStateFlow(false)

    /**
     * Whether any branch of the current cleanup failed or was cancelled. Such a run did not
     * "finish with nothing to free" — the user gets an error dialog, or stopped it themselves —
     * so the zero-result card must stay away and not contradict that.
     */
    private val cleanupFailed = MutableStateFlow(false)

    /** While [discardResults] clears the tools one by one, suppress the hero so it never shows partial data. */
    private val discarding = MutableStateFlow(false)

    private val nowTicks: Flow<Instant> = intervalFlow(1.minutes).map { Instant.now() }

    private val heroExpanded = MutableStateFlow(false)

    /**
     * Orders the auto-expand resolution against collapse-on-disable, so a disable landing between
     * the decision and the expand write cannot be overwritten by a stale expand.
     */
    private val expansionLock = Mutex()

    /**
     * Whether the hero card is expanded. It only ever becomes true as the outcome of a one-tap main
     * action that produced something to show, or because the user expanded it from the bar's compact
     * chip — an operation started anywhere else (a tool card, an in-tool screen, the scheduler)
     * leaves it collapsed. Dismissing or discarding collapses it again. In-memory, resets with the VM.
     *
     * The auto-expansion half is additionally gated by [GeneralSettings.dashboardHeroAutoShow]: with
     * the setting off no action opens the card by itself, the chip-expand path is unaffected, and
     * turning the setting off collapses a card that is currently open.
     */
    val isHeroExpanded: StateFlow<Boolean> = heroExpanded

    /**
     * One consistent snapshot of everything the hero/main action derives from; null once the
     * derivation has failed and fallen back. Shared eagerly because the auto-expand resolution has
     * to observe it too, and both consumers must judge the *same* snapshot.
     */
    private data class HeroState(
        val actionState: BottomBarState.Action,
        val activeTasks: Int,
        val queuedTasks: Int,
        val heroSummary: HeroSummary?,
        val upgradeInfo: UpgradeRepo.Info?,
        /** The batch this snapshot was derived under; see [BatchState.id]. */
        val batchId: Long,
        /** All branches of that batch have folded in their results. */
        val isSettled: Boolean,
        /** [GeneralSettings.dashboardHeroAutoShow] as of this snapshot; see [resolveAutoExpand]. */
        val autoShowEnabled: Boolean,
    )

    /** Consecutive failures of the [heroState] derivation; 0 while it is healthy. */
    private var heroStateFailures = 0L

    /**
     * The batch phase is applied by *re-deriving* rather than as another combine input: a branch
     * writes its tool state before it decrements, but the two reach a combine through independent
     * per-source collectors, so a plain combine can pair the settled counter with a tool state it
     * hasn't picked up yet — and the auto-expand resolution would judge that stale pairing. Keying
     * the restart on the phase makes every source re-read at (re)subscription, after the decrement.
     *
     * Keyed on [BatchPhase], NOT on the raw counter: the intermediate 4→3→2→1 decrements are not
     * phase changes, so a run costs two re-subscriptions (start and settle) instead of five.
     *
     * ACCEPTED RESIDUAL — do not "fix" this without asking: re-subscribing does not *force* the
     * tools to recompute. Each tool's `state` is a `replayingShare` projection, so a re-read returns
     * its replay cache; if a tool's own projection hasn't published the scan it just finished by the
     * time the settle edge arrives, the run resolves against data that doesn't include it and
     * disarms without expanding. Effect is benign and rare: the hero doesn't auto-open, the bar
     * still shows the compact summary chip, and one tap opens it. Closing it would mean exposing
     * each tool's internal data as a new public StateFlow across the four app-tool-* modules; that
     * API cost was weighed against this miss and deliberately declined.
     */
    private val heroState: Flow<HeroState?> = batchState
        .map { BatchPhase(id = it.id, isSettled = it.pending == 0) }
        .distinctUntilChanged()
        .flatMapLatest<BatchPhase, HeroState?> { phase ->
            eu.darken.sdmse.common.flow.combine(
                upgradeInfo,
                taskManager.state,
                corpseFinder.state,
                systemCleaner.state,
                appCleaner.state,
                deduplicator.state,
                generalSettings.enableDashboardOneClick.flow,
                oneClickOptionsState,
                freedResult,
                discarding,
                generalSettings.dashboardHeroAutoShow.flow,
            ) { upgradeInfo,
                taskState,
                corpseState,
                filterState,
                junkState,
                dedupeState,
                oneClickMode,
                oneClickOptions,
                freed,
                isDiscarding,
                autoShowEnabled ->

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
                // Post-scan "will be freed" takes priority; otherwise, once the action has settled,
                // show the "freed" result of the last main-action deletion/one-click. While working,
                // both stay hidden and the bar carries progress. The settle edge gates this too, not
                // just the idle task manager: the latter reports idle again before the branches fold
                // in their results, and without the gate the identical "can be freed" card flashes
                // in that window.
                //
                // Deliberately NOT gated on DELETE: a non-Pro user whose only findings are locked
                // resolves to SCAN/ONECLICK, and that is exactly the state the LOCKED_ONLY card
                // exists for. Nothing else slips through — [resolveMainAction]'s DELETE conditions
                // are [buildHeroSummary]'s inclusion conditions, so the builder is already the
                // filter and returns null wherever the DELETE check used to.
                val freeable = if (phase.isSettled &&
                    actionState != BottomBarState.Action.WORKING &&
                    actionState != BottomBarState.Action.WORKING_CANCELABLE
                ) {
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
                val provenance = freedProvenance
                val settled = freed
                    ?.takeIf { taskState.isIdle && phase.isSettled }
                    ?.takeIf { taskState.vouchedFor(provenance) }
                    ?.let { outcome ->
                        // What the run left behind, across the tools it submitted to. Recomputed per
                        // emission from live data, so it tracks later exclusions and per-tool deletes.
                        val residue = residueOf(
                            corpse = corpseState.data,
                            system = filterState.data,
                            app = junkState.data,
                            dedupe = dedupeState.data,
                            tools = provenance?.submitted.orEmpty(),
                        )
                        // Pro-gated findings that survived alongside the outcome, minus anything the
                        // run submitted to. That filter is not cosmetic: isProForUi() fails open, so
                        // a cleanup can submit a Pro-gated tool while this combine's [upgradeInfo]
                        // still reports non-Pro — that tool's leftovers would then be counted by
                        // [residueOf] *and* returned here, and the card would report the same bytes
                        // twice under contradictory framing ("5 MB left" and "unlock 5 MB more").
                        val locked = lockedSlices(
                            app = junkState.data,
                            dedupe = dedupeState.data,
                            oneClick = oneClickOptions,
                            isPro = upgradeInfo?.isPro == true,
                        ).filterNot { it.type in provenance?.submitted.orEmpty() }
                        outcome.copy(
                            residueSize = residue?.size ?: 0L,
                            residueCount = residue?.count ?: 0,
                            lockedTools = locked,
                        )
                    }
                // A settled cleanup outranks [freeable] whatever it freed: whatever data survived it
                // rebuilds into a FREEABLE card that buries the fact the deletion ran at all. That is
                // obvious when it freed nothing, but it also holds when it freed plenty and left a
                // remainder — some junk can never be cleared (a locked system app's cache), so a
                // cleanup routinely leaves one behind, and reporting only that remainder reads as if
                // the run found almost nothing.
                val heroSummary = (settled ?: freeable).takeIf { !isDiscarding }
                HeroState(
                    actionState = actionState,
                    activeTasks = taskState.tasks.filter { it.isActive }.size,
                    queuedTasks = taskState.tasks.filter { it.isQueued }.size,
                    heroSummary = heroSummary,
                    upgradeInfo = upgradeInfo,
                    batchId = phase.id,
                    isSettled = phase.isSettled,
                    autoShowEnabled = autoShowEnabled,
                )
            }
        }
        // Upstream of the retry on purpose: only a real snapshot ends an outage. The fallback the
        // retry emits below goes straight downstream and must not clear the counter.
        .onEach { heroStateFailures = 0L }
        // The share below is eager and permanently subscribed, so there is no later subscription
        // cycle to restart this on — a terminating operator (`catch`, or a bounded retry) would cost
        // the dashboard its whole bottom bar for the ViewModel's lifetime over one DataStore blip.
        // Retry without a bound instead, emitting the null fallback (the bar's "not ready" state)
        // so the outage is visible, reporting it once per outage rather than per attempt, and
        // backing off so a permanently broken source can't hot-loop.
        .retryWhen { error, _ ->
            if (error is CancellationException) return@retryWhen false
            if (heroStateFailures == 0L) {
                log(TAG, ERROR) { "heroState failed: ${error.asLog()}" }
                onStateError(error)
            }
            emit(null)
            val doublings = heroStateFailures.coerceAtMost(HERO_STATE_RETRY_MAX_DOUBLINGS).toInt()
            val backoff = minOf(HERO_STATE_RETRY_MAX_MS, HERO_STATE_RETRY_BASE_MS shl doublings)
            heroStateFailures++
            delay(backoff)
            true
        }
        .shareIn(scope, SharingStarted.Eagerly, replay = 1)

    init {
        // Baseline for the provenance a settle snapshots: which task execution is currently on record
        // for each cleanup tool. Tracked continuously because the branch that settles cannot read the
        // task manager's state flow synchronously.
        scope.launch {
            taskManager.state.collect { state ->
                tasksOnRecord = state.tasks
                    .filter { it.isComplete && it.toolType in CLEANUP_TOOLS }
                    .associate { it.toolType to it.task }
            }
        }
        // A freshly completed *scan* clears any stale "freed" result. We must only react to a
        // *strictly newer* scan time: TaskManager keeps one task per tool, so a delete prunes that
        // tool's scan result and would otherwise make this "change" to an older/absent scan time
        // and wrongly clear the freed hero we just produced.
        scope.launch {
            var latestSeenScan: Instant? = null
            taskManager.state
                .mapNotNull { state -> state.latestScanTimes().values.maxOrNull() }
                .collect { scanCompletedAt ->
                    val prev = latestSeenScan
                    if (prev == null || scanCompletedAt.isAfter(prev)) {
                        latestSeenScan = scanCompletedAt
                        freedResult.value = null
                    }
                }
        }
        // Auto-expand is a one-shot armed by a one-tap main action and resolved when that action's
        // branches have all settled: expand if it produced something to show, otherwise just disarm.
        // Resolving either way is the point — an arm left set by a fruitless scan would be inherited
        // by the next card-triggered scan and auto-expand it, which is the defect this closes.
        scope.launch {
            heroState.filterNotNull().collect { hero ->
                // Under the lock as one unit — the batch read, the decision and the expand write:
                // a disable arriving in between must not lose to a decision taken before it.
                expansionLock.withLock {
                    val decision = resolveAutoExpand(
                        snapshotBatchId = hero.batchId,
                        isSettled = hero.isSettled,
                        hasSummary = hero.heroSummary != null,
                        // From the same snapshot being judged, never read separately: the setting is a
                        // combine input so it is causally consistent with the rest of the snapshot.
                        autoShowEnabled = hero.autoShowEnabled,
                        batch = batchState.value,
                    )
                    when (decision) {
                        AutoExpandDecision.IGNORE -> return@withLock
                        AutoExpandDecision.DISARM -> Unit
                        AutoExpandDecision.EXPAND_AND_DISARM -> heroExpanded.value = true
                    }
                    batchState.update { if (it.id == hero.batchId) it.copy(autoExpandArmed = false) else it }
                }
            }
        }
        // Disabling auto-show collapses an open hero so the user does not return from settings to the
        // exact card they just disabled. DataStoreValue.flow is distinctUntilChanged, so only real
        // value changes arrive; the initial emission is a no-op (heroExpanded starts false).
        scope.launch {
            // Consecutive failures of this collector; 0 while it is healthy.
            var failures = 0L
            generalSettings.dashboardHeroAutoShow.flow
                // Upstream of the retry: only a genuine emission ends an outage.
                .onEach { failures = 0L }
                // Same treatment as heroState: a settings read that blips must not silently take
                // the collapse-on-disable behaviour away for the ViewModel's lifetime. Retrying
                // resubscribes DataStoreValue.flow, which re-emits the current value on
                // resubscription, so a disable that happened during the outage is delivered on
                // recovery. Reported once per outage rather than per attempt, with backoff so a
                // permanently broken source can't hot-loop.
                .retryWhen { error, _ ->
                    if (error is CancellationException) return@retryWhen false
                    if (failures == 0L) {
                        log(TAG, ERROR) { "dashboardHeroAutoShow collector failed: ${error.asLog()}" }
                        onStateError(error)
                    }
                    val doublings = failures.coerceAtMost(HERO_STATE_RETRY_MAX_DOUBLINGS).toInt()
                    val backoff = minOf(HERO_STATE_RETRY_MAX_MS, HERO_STATE_RETRY_BASE_MS shl doublings)
                    failures++
                    delay(backoff)
                    true
                }
                .filter { !it }
                .collect {
                    expansionLock.withLock {
                        heroExpanded.value = false
                        // Mirrors dismissHero(): closes the small race where a snapshot derived before
                        // the disable-write settles after it and re-opens the card.
                        batchState.update { it.copy(autoExpandArmed = false) }
                    }
                }
        }
    }

    /**
     * Cold bottom bar state assembly on top of the shared [heroState]; null while the hero
     * derivation has no (or no longer a) usable snapshot. [listIsReady] is the VM's shared flow
     * (derived from its listState) so its upstream isn't collected twice.
     */
    fun bottomBarState(
        listIsReady: Flow<Boolean>,
    ): Flow<BottomBarState?> = combine(
        heroState,
        listIsReady,
        nowTicks,
    ) { hero, listReady, now ->
        hero?.let {
            BottomBarState(
                isReady = listReady,
                actionState = it.actionState,
                activeTasks = it.activeTasks,
                queuedTasks = it.queuedTasks,
                heroSummary = it.heroSummary,
                upgradeInfo = it.upgradeInfo,
                now = now,
            )
        }
    }

    fun dismissHero() {
        log(TAG) { "dismissHero()" }
        heroExpanded.value = false
        // An action still in flight must not re-open what the user just closed.
        batchState.update { it.copy(autoExpandArmed = false) }
    }

    /** Expands a collapsed hero (via the compact summary chip in the bar). */
    fun expandHero() {
        log(TAG) { "expandHero()" }
        heroExpanded.value = true
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
            // Discard returns the dashboard to its pristine state; the next card-triggered scan
            // must not inherit this run's auto-expand and pop the hero open by itself.
            heroExpanded.value = false
            batchState.update { it.copy(autoExpandArmed = false) }
        } finally {
            discarding.value = false
        }
    }

    /**
     * Runs one main-action tool branch. A branch of a run that opened a batch ([isTracked]) also
     * closes its share of it, so the freed hero only appears — and auto-expand only resolves — once
     * *all* branches are done. Cancel taps don't open a batch and must not decrement someone else's.
     */
    private fun launchMainBranch(isTracked: Boolean, isCleanup: Boolean, block: suspend () -> Unit) = scope.launch {
        var failed = false
        try {
            block()
        } catch (e: Throwable) {
            // Cancellation counts too: a run the user stopped did not "finish".
            failed = true
            throw e
        } finally {
            if (isTracked) {
                if (isCleanup && failed) cleanupFailed.value = true
                // Two counters on purpose, do NOT merge them back into one: the observed
                // [batchState] is published *last*, after this branch wrote everything it produces.
                // Its pending == 0 is the settle edge [heroState] snapshots and auto-expand acts on,
                // so a result written after that edge would arrive too late to be part of that
                // snapshot. [branchesInFlight] is the unobserved bookkeeping that tells us we are
                // the last branch while there is still time to write.
                val remaining = branchesInFlight.updateAndGet { (it - 1).coerceAtLeast(0) }
                // Last branch home. A cleanup that ran but freed nothing leaves the tools' data
                // untouched, so the identical "can be freed" card would just re-render and the
                // whole thing reads as "the button did nothing". Say so instead.
                if (isCleanup && remaining == 0 && cleanupRan.value && !cleanupFailed.value) {
                    val nothingFreed = HeroSummary(
                        mode = HeroSummary.Mode.NOTHING_FREED,
                        totalSize = 0L,
                        itemCount = 0,
                        tools = emptyList(),
                        timestamp = Instant.now(),
                    )
                    // Fold rather than assign: a result arriving from an overlapping cleanup must
                    // win over this placeholder, never be clobbered by it. The lambda stays pure —
                    // `update` retries it on contention.
                    val applied = freedResult.updateAndGet { current -> current ?: nothingFreed }
                    if (applied === nothingFreed) log(TAG, INFO) { "Cleanup finished without freeing anything." }
                }
                // Published by the last branch, so it covers every task this cleanup submitted. Set
                // after the fold above and before [batchState] settles, i.e. the outcome is never
                // visible without the provenance that vouches for it.
                if (isCleanup && remaining == 0) {
                    val submitted = submittedTasks.value
                    val baseline = tasksOnRecord
                    freedProvenance = FreedProvenance(
                        acceptable = CLEANUP_TOOLS.associateWith { type ->
                            listOfNotNull(baseline[type], submitted[type])
                        },
                        submitted = submitted.keys,
                    )
                }
                // Every branch publishes its own decrement, so this reaches 0 only after the last
                // one has been through the block above — no matter which got there first.
                batchState.update { it.copy(pending = (it.pending - 1).coerceAtLeast(0)) }
            }
        }
    }

    /**
     * Whether a Pro-free tool will actually contribute to this cleanup: it has findings *and* is
     * opted into one-click. The upgrade upsell is only appropriate when no free tool would run, so
     * this has to mirror [resolveMainAction]'s arming conditions — testing raw scan data instead
     * suppresses the upsell for a tool that is opted out and therefore about to be skipped, which
     * leaves the whole cleanup doing nothing at all.
     */
    private suspend fun freeToolsWillRun(): Boolean {
        val corpse = corpseFinder.state.first().data.hasData && generalSettings.oneClickCorpseFinderEnabled.value()
        val system = systemCleaner.state.first().data.hasData && generalSettings.oneClickSystemCleanerEnabled.value()
        return corpse || system
    }

    /**
     * Runs the one-tap main action. Only the dashboard FAB (and the DELETE confirmation dialog it
     * opens) may call this — that FAB-only origin is what makes arming the auto-expand here correct.
     * Scans and deletions started from a tool card, an in-tool screen or the scheduler must not.
     */
    fun mainAction(actionState: BottomBarState.Action) {
        log(TAG) { "mainAction(actionState=$actionState)" }
        // Start a fresh "freed" tally for this deletion/one-click. The hero stays hidden until every
        // branch has settled (batch pending == 0) so a partial per-tool result can't flash.
        val isCleanup = actionState == BottomBarState.Action.DELETE || actionState == BottomBarState.Action.ONECLICK
        // WORKING/WORKING_CANCELABLE are the cancel tap, not a new run: they neither open a batch
        // nor arm anything.
        val startsRun = isCleanup || actionState == BottomBarState.Action.SCAN
        // Single-flight across *every* run, scans included: a second batch would reset the tally
        // under the branches still running on the first, so both counters would hit 0 after any
        // four of the eight branches returned — settling the hero while tasks are still going and
        // attributing results to the wrong batch. Reachable by double-tapping SCAN before the task
        // manager reports non-idle, which is the window in which the FAB still offers SCAN. A
        // second run would also gain nothing: every task queues on its tool's resource lock. Safe
        // without a lock — this is not a suspend function and only the main thread calls it, so
        // taps serialize.
        if (startsRun && batchState.value.pending > 0) {
            log(TAG, WARN) { "mainAction($actionState): a main action is already in flight, ignoring." }
            return
        }
        if (startsRun) {
            // One atomic publish: a settle edge must never be visible without the id and arm it
            // belongs to. Counters before the cleanup bookkeeping below.
            branchesInFlight.set(4) // CorpseFinder + SystemCleaner + AppCleaner + Deduplicator
            batchState.update { BatchState(id = it.id + 1, pending = 4, autoExpandArmed = true) }
            submittedTasks.value = emptyMap()
            // Dropped for scans too, not just cleanups: a scan that is cancelled or submits nothing
            // never completes, so the successful-scan collector below would not clear [freedResult]
            // and an un-vouched outcome would otherwise linger unchallenged.
            freedProvenance = null
        }
        if (isCleanup) {
            freedResult.value = null
            cleanupRan.value = false
            cleanupFailed.value = false
            // Stamp before any branch runs so it's <= every resulting report's end_at.
            freedResultSince = Instant.now()
        }
        launchMainBranch(isTracked = startsRun, isCleanup = isCleanup) {
            if (!generalSettings.oneClickCorpseFinderEnabled.value()) {
                log(VERBOSE) { "CorpseFinder is disabled one-click mode." }
                return@launchMainBranch
            }

            when (actionState) {
                BottomBarState.Action.SCAN -> submitTask(CorpseFinderScanTask())
                BottomBarState.Action.WORKING_CANCELABLE -> taskManager.cancel(SDMTool.Type.CORPSEFINDER)
                BottomBarState.Action.WORKING -> {}
                BottomBarState.Action.DELETE -> if (corpseFinder.state.first().data != null) {
                    runCleanup(SDMTool.Type.CORPSEFINDER, CorpseFinderDeleteTask())
                }

                BottomBarState.Action.ONECLICK -> runCleanup(SDMTool.Type.CORPSEFINDER, CorpseFinderOneClickTask())
            }
        }
        launchMainBranch(isTracked = startsRun, isCleanup = isCleanup) {
            if (!generalSettings.oneClickSystemCleanerEnabled.value()) {
                log(VERBOSE) { "SystemCleaner is disabled one-click mode." }
                return@launchMainBranch
            }

            when (actionState) {
                BottomBarState.Action.SCAN -> submitTask(SystemCleanerScanTask())
                BottomBarState.Action.WORKING_CANCELABLE -> taskManager.cancel(SDMTool.Type.SYSTEMCLEANER)
                BottomBarState.Action.WORKING -> {}
                BottomBarState.Action.DELETE -> if (systemCleaner.state.first().data != null) {
                    runCleanup(SDMTool.Type.SYSTEMCLEANER, SystemCleanerProcessingTask())
                }

                BottomBarState.Action.ONECLICK -> runCleanup(SDMTool.Type.SYSTEMCLEANER, SystemCleanerOneClickTask())
            }
        }
        launchMainBranch(isTracked = startsRun, isCleanup = isCleanup) {
            if (!generalSettings.oneClickAppCleanerEnabled.value()) {
                log(VERBOSE) { "AppCleaner is disabled one-click mode." }
                return@launchMainBranch
            }

            when (actionState) {
                BottomBarState.Action.SCAN -> submitTask(AppCleanerScanTask())
                BottomBarState.Action.WORKING_CANCELABLE -> taskManager.cancel(SDMTool.Type.APPCLEANER)
                BottomBarState.Action.WORKING -> {}
                BottomBarState.Action.DELETE -> {
                    if (appCleaner.state.first().data.hasActionableData && upgradeRepo.isProForUi()) {
                        runCleanup(SDMTool.Type.APPCLEANER, AppCleanerProcessingTask())
                    } else if (appCleaner.state.first().data.hasActionableData && !freeToolsWillRun()) {
                        onUpgradeRequired()
                    }
                }

                BottomBarState.Action.ONECLICK -> {
                    if (upgradeRepo.isProForUi()) {
                        runCleanup(SDMTool.Type.APPCLEANER, AppCleanerOneClickTask())
                    } else if (appCleaner.state.first().data.hasActionableData && !freeToolsWillRun()) {
                        onUpgradeRequired()
                    }
                }
            }
        }
        launchMainBranch(isTracked = startsRun, isCleanup = isCleanup) {
            if (!generalSettings.oneClickDeduplicatorEnabled.value()) {
                log(VERBOSE) { "Deduplicator is disabled one-click mode." }
                return@launchMainBranch
            }

            when (actionState) {
                BottomBarState.Action.SCAN -> submitTask(DeduplicatorScanTask())
                BottomBarState.Action.WORKING_CANCELABLE -> taskManager.cancel(SDMTool.Type.DEDUPLICATOR)
                BottomBarState.Action.WORKING -> {}
                BottomBarState.Action.DELETE -> if (deduplicator.state.first().data != null && upgradeRepo.isProForUi()) {
                    runCleanup(SDMTool.Type.DEDUPLICATOR, DeduplicatorDeleteTask())
                }

                BottomBarState.Action.ONECLICK -> {
                    if (upgradeRepo.isProForUi()) {
                        runCleanup(SDMTool.Type.DEDUPLICATOR, DeduplicatorOneClickTask())
                    } else if (deduplicator.state.first().data.hasData && !freeToolsWillRun()) {
                        onUpgradeRequired()
                    }
                }
            }
        }
    }

    /**
     * Submits one branch's cleanup task and folds its result in. Records the task instance first so
     * [freedProvenance] can later tell this run's results apart from anyone else's.
     */
    private suspend fun runCleanup(type: SDMTool.Type, task: SDMTool.Task) {
        submittedTasks.update { it + (type to task) }
        try {
            accumulateFreed(type, submitTask(task))
        } catch (e: Throwable) {
            // A tool that failed after freeing something records both on its completed task. The
            // failure still propagates to the error dialog, but what it did free belongs in the hero.
            if (e !is CancellationException) {
                taskManager.state.first().getLatestTask(type)
                    ?.takeIf { it.task === task && it.result != null && it.error != null }
                    ?.result?.let { accumulateFreed(type, it) }
            }
            throw e
        }
    }

    /** Folds a deletion/one-click result into [freedResult] so the hero can show what was freed. */
    private fun accumulateFreed(type: SDMTool.Type, result: SDMTool.Task.Result) {
        // Reaching here means a deletion task ran for this tool, even if it turned out to free
        // nothing; [launchMainBranch] needs that distinction to report a zero-result cleanup.
        cleanupRan.value = true
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

        /** The tools the main action cleans, i.e. the ones whose data the freed hero summarises. */
        private val CLEANUP_TOOLS = setOf(
            SDMTool.Type.CORPSEFINDER,
            SDMTool.Type.SYSTEMCLEANER,
            SDMTool.Type.APPCLEANER,
            SDMTool.Type.DEDUPLICATOR,
        )

        /** Hero-derivation retry backoff: 1s, doubling per consecutive failure, capped at a minute. */
        private const val HERO_STATE_RETRY_BASE_MS = 1_000L
        private const val HERO_STATE_RETRY_MAX_MS = 60_000L
        private const val HERO_STATE_RETRY_MAX_DOUBLINGS = 6L

        /**
         * What to do with one hero snapshot the settle collector sees. Pure so the whole table can
         * be tested — in particular the id-mismatch row, whose timing (a resolution derived from an
         * earlier run arriving after the next run armed, the two arms being indistinguishable
         * booleans) only occurs across threads and cannot be staged on a test dispatcher.
         *
         * A settled snapshot of the armed batch always consumes the arm, whether or not it had
         * anything to show: an arm left behind by a fruitless run would be inherited by the next
         * card-triggered scan and auto-expand it.
         *
         * [autoShowEnabled] is [GeneralSettings.dashboardHeroAutoShow], taken from the very snapshot
         * being judged. With it off nothing auto-expands — but the row DISARMs rather than IGNOREs,
         * because the arm still has to be consumed or a later card-triggered scan would inherit it.
         * It deliberately sits *after* the settle check: an unsettled snapshot must stay IGNORE, or
         * the setting would consume the arm before the run it belongs to has finished.
         */
        internal fun resolveAutoExpand(
            snapshotBatchId: Long,
            isSettled: Boolean,
            hasSummary: Boolean,
            autoShowEnabled: Boolean,
            batch: BatchState,
        ): AutoExpandDecision = when {
            !isSettled -> AutoExpandDecision.IGNORE
            !batch.autoExpandArmed -> AutoExpandDecision.IGNORE
            batch.id != snapshotBatchId -> AutoExpandDecision.IGNORE
            !autoShowEnabled -> AutoExpandDecision.DISARM
            hasSummary -> AutoExpandDecision.EXPAND_AND_DISARM
            else -> AutoExpandDecision.DISARM
        }

        /**
         * Resolves what the main dashboard button does. Every tool arms DELETE only when it is
         * opted into one-click, because [mainAction] skips a tool whose toggle is off — arming
         * without that check offers a button that provably frees nothing. Deduplicator
         * additionally requires Pro; its primary delete flow remains in-tool cluster selection.
         * AppCleaner deliberately arms *without* Pro so [mainAction] can upsell instead of
         * deleting, which is why no [isPro] check appears on its line.
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
            corpse.hasData && oneClick.corpseFinderEnabled -> BottomBarState.Action.DELETE
            system.hasData && oneClick.systemCleanerEnabled -> BottomBarState.Action.DELETE
            app.hasActionableData && oneClick.appCleanerEnabled -> BottomBarState.Action.DELETE
            dedupe.hasData && oneClick.deduplicatorEnabled && isPro -> BottomBarState.Action.DELETE
            oneClickMode -> BottomBarState.Action.ONECLICK
            else -> BottomBarState.Action.SCAN
        }

        /**
         * The Pro-gated findings this user cannot act on: AppCleaner and Deduplicator, when they
         * have data and are opted into one-click. Empty for a Pro user, whose findings are simply
         * freeable.
         *
         * The one-click condition mirrors [buildHeroSummary]/[resolveMainAction] on purpose: a tool
         * the user switched off is not locked, it is opted out, and claiming Pro would unlock it
         * would be false.
         */
        internal fun lockedSlices(
            app: AppCleaner.Data?,
            dedupe: Deduplicator.Data?,
            oneClick: OneClickOptionsState,
            isPro: Boolean,
        ): List<HeroSummary.ToolSlice> {
            if (isPro) return emptyList()
            return buildList {
                app?.takeIf { oneClick.appCleanerEnabled && it.hasActionableData }?.let {
                    add(HeroSummary.ToolSlice(SDMTool.Type.APPCLEANER, it.actionableSize, it.actionableCount))
                }
                dedupe?.takeIf { oneClick.deduplicatorEnabled && it.hasData }?.let {
                    add(HeroSummary.ToolSlice(SDMTool.Type.DEDUPLICATOR, it.redundantSize, it.redundantCount))
                }
            }
        }

        /**
         * Builds the action-truthful hero summary: [HeroSummary.tools] holds only tools the main
         * DELETE action will actually free (one-click toggle on, has data, AppCleaner and
         * Deduplicator additionally require Pro), while Pro-gated findings this user cannot act on
         * go to [HeroSummary.lockedTools] as an upsell. Returns null only when there is nothing at
         * all to say — neither freeable nor locked.
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
                app?.takeIf { oneClick.appCleanerEnabled && isPro && it.hasActionableData }?.let {
                    add(HeroSummary.ToolSlice(SDMTool.Type.APPCLEANER, it.actionableSize, it.actionableCount))
                }
                dedupe?.takeIf { oneClick.deduplicatorEnabled && isPro && it.hasData }?.let {
                    add(HeroSummary.ToolSlice(SDMTool.Type.DEDUPLICATOR, it.redundantSize, it.redundantCount))
                }
            }
            val locked = lockedSlices(app = app, dedupe = dedupe, oneClick = oneClick, isPro = isPro)
            if (tools.isEmpty()) {
                if (locked.isEmpty()) return null
                // Nothing this user can free, but Pro-gated findings are sitting there. The amounts
                // stay 0 — they are what the main action delivers, and it delivers nothing here.
                return HeroSummary(
                    mode = HeroSummary.Mode.LOCKED_ONLY,
                    totalSize = 0L,
                    itemCount = 0,
                    tools = emptyList(),
                    timestamp = locked.mapNotNull { scanTimes[it.type] }.maxOrNull(),
                    lockedTools = locked,
                )
            }
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
                lockedTools = locked,
            )
        }

        /**
         * What is still on the books for [tools], i.e. the tools a settled cleanup submitted to.
         *
         * Scoped to those rather than to everything with data: a tool the run never touched — one
         * switched off in the one-click options — has leftovers that this cleanup neither freed nor
         * failed to free, and counting them would make an otherwise complete run look partial.
         *
         * Read live rather than snapshotted at the settle edge on purpose. This only feeds a
         * displayed number, so a tool state that has not published its post-delete data yet simply
         * corrects itself on the next emission. (A snapshot would be wrong here for the same reason
         * it is unusable as a staleness signal — see [vouchedFor].)
         */
        internal data class Residue(val size: Long, val count: Int)

        internal fun residueOf(
            corpse: CorpseFinder.Data?,
            system: SystemCleaner.Data?,
            app: AppCleaner.Data?,
            dedupe: Deduplicator.Data?,
            tools: Set<SDMTool.Type>,
        ): Residue? {
            val slices = buildList {
                corpse?.takeIf { SDMTool.Type.CORPSEFINDER in tools && it.hasData }?.let {
                    add(HeroSummary.ToolSlice(SDMTool.Type.CORPSEFINDER, it.totalSize, it.totalCount))
                }
                system?.takeIf { SDMTool.Type.SYSTEMCLEANER in tools && it.hasData }?.let {
                    add(HeroSummary.ToolSlice(SDMTool.Type.SYSTEMCLEANER, it.totalSize, it.totalCount))
                }
                app?.takeIf { SDMTool.Type.APPCLEANER in tools && it.hasData }?.let {
                    add(HeroSummary.ToolSlice(SDMTool.Type.APPCLEANER, it.totalSize, it.totalCount))
                }
                dedupe?.takeIf { SDMTool.Type.DEDUPLICATOR in tools && it.hasData }?.let {
                    add(HeroSummary.ToolSlice(SDMTool.Type.DEDUPLICATOR, it.redundantSize, it.redundantCount))
                }
            }
            return Residue(size = slices.sumOf { it.size }, count = slices.sumOf { it.count })
        }
    }
}

/**
 * Whether a freed hero backed by [provenance] still describes the current state.
 *
 * The task manager keeps one completed entry per tool, so for every tool the cleanup touched, that
 * entry should still be the execution the cleanup submitted. If it is not, something else has since
 * cleaned that tool — its own delete screen, the scheduler, the one-tap shortcut — and the freed
 * total no longer matches what is on disk.
 *
 * Identity, not equality: the task types are data classes, so an externally submitted
 * `CorpseFinderDeleteTask()` is `==` to ours and would otherwise vouch for a run it had nothing to
 * do with. Tools whose entry has not landed yet are skipped rather than treated as foreign, so a
 * cleanup is never briefly judged stale on its own results.
 *
 * A null [provenance] means nothing is vouching for the outcome, and the caller falls back to live
 * data — the check fails closed.
 */
internal fun TaskSubmitter.State.vouchedFor(provenance: DashboardMainActionEngine.FreedProvenance?): Boolean {
    if (provenance == null) return false
    return provenance.acceptable.all { (type, acceptable) ->
        val onRecord = tasks.filter { it.isComplete && it.toolType == type }
        // `all`, not `any`: completion publishes the new entry before the task manager prunes the old
        // one, so for a moment both are on record. Requiring every entry to be acceptable means that
        // window cannot vouch for a foreign result just because the batch's own is still sitting there.
        onRecord.isEmpty() || onRecord.all { entry -> acceptable.any { it === entry.task } }
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
