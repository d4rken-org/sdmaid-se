package eu.darken.sdmse.main.ui.dashboard

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.analyzer.core.Analyzer
import eu.darken.sdmse.common.navigation.routes.DeviceStorageRoute
import eu.darken.sdmse.appcleaner.core.AppCleaner
import eu.darken.sdmse.appcleaner.core.hasData
import eu.darken.sdmse.appcleaner.ui.AppCleanerListRoute
import eu.darken.sdmse.appcleaner.ui.AppJunkDetailsRoute
import eu.darken.sdmse.common.navigation.routes.AppControlListRoute
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerOneClickTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerProcessingTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerScanTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerSchedulerTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerTask
import eu.darken.sdmse.appcontrol.core.AppControl
import eu.darken.sdmse.common.SdmSeLinks
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.corpsefinder.ui.CorpseDetailsRoute
import eu.darken.sdmse.corpsefinder.ui.CorpseFinderListRoute
import eu.darken.sdmse.common.flow.SingleEventFlow
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.datastore.valueBlocking
import eu.darken.sdmse.common.debug.DebugCardProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.debug.recorder.core.DebugLogSession
import eu.darken.sdmse.common.debug.recorder.core.DebugLogSessionManager
import eu.darken.sdmse.common.debug.recorder.core.RecorderModule
import eu.darken.sdmse.common.debug.recorder.core.SessionId
import eu.darken.sdmse.main.ui.dashboard.cards.DebugRecorderDashboardCardItem
import eu.darken.sdmse.common.debug.recorder.ui.RecorderActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.flow.intervalFlow
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.review.ReviewTool
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.common.updater.UpdateService
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.isPro
import eu.darken.sdmse.corpsefinder.core.CorpseFinder
import eu.darken.sdmse.corpsefinder.core.hasData
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderDeleteTask
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderOneClickTask
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderScanTask
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderSchedulerTask
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderTask
import eu.darken.sdmse.corpsefinder.core.tasks.UninstallWatcherTask
import eu.darken.sdmse.deduplicator.core.Deduplicator
import eu.darken.sdmse.deduplicator.ui.DeduplicatorDetailsRoute
import eu.darken.sdmse.deduplicator.ui.DeduplicatorListRoute
import eu.darken.sdmse.deduplicator.core.hasData
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorDeleteTask
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorOneClickTask
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorScanTask
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorTask
import eu.darken.sdmse.main.core.DashboardCardConfig
import eu.darken.sdmse.main.core.DashboardCardType
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.motd.MotdRepo
import eu.darken.sdmse.main.core.release.ReleaseManager
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import eu.darken.sdmse.main.core.taskmanager.getLatestResult
import eu.darken.sdmse.main.ui.dashboard.cards.AnalyzerDashboardCardItem
import eu.darken.sdmse.main.ui.dashboard.cards.DashboardItem
import eu.darken.sdmse.main.ui.dashboard.cards.AnniversaryDashboardCardItem
import eu.darken.sdmse.main.ui.dashboard.cards.AppControlDashboardCardItem
import eu.darken.sdmse.main.ui.dashboard.cards.DebugDashboardCardItem
import eu.darken.sdmse.main.ui.dashboard.cards.ErrorDataAreaDashboardCardItem
import eu.darken.sdmse.main.ui.dashboard.cards.MotdDashboardCardItem
import eu.darken.sdmse.main.ui.dashboard.cards.ReviewDashboardCardItem
import eu.darken.sdmse.main.ui.dashboard.cards.SchedulerDashboardCardItem
import eu.darken.sdmse.main.ui.dashboard.cards.SetupDashboardCardItem
import eu.darken.sdmse.main.ui.dashboard.cards.SqueezerDashboardCardItem
import eu.darken.sdmse.main.ui.dashboard.cards.StatsDashboardCardItem
import eu.darken.sdmse.main.ui.dashboard.cards.SwiperDashboardCardItem
import eu.darken.sdmse.main.ui.dashboard.cards.TitleDashboardCardItem
import eu.darken.sdmse.main.ui.dashboard.cards.ToolDashboardCardItem
import eu.darken.sdmse.main.ui.dashboard.cards.UpdateDashboardCardItem
import eu.darken.sdmse.main.ui.dashboard.cards.UpgradeDashboardCardItem
import eu.darken.sdmse.scheduler.core.SchedulerManager
import eu.darken.sdmse.scheduler.ui.SchedulerManagerRoute
import eu.darken.sdmse.setup.SetupManager
import eu.darken.sdmse.setup.SetupRoute
import eu.darken.sdmse.squeezer.core.Squeezer
import eu.darken.sdmse.squeezer.core.SqueezerSettings
import eu.darken.sdmse.squeezer.ui.SqueezerListRoute
import eu.darken.sdmse.squeezer.ui.SqueezerSetupRoute
import eu.darken.sdmse.squeezer.core.tasks.SqueezerProcessTask
import eu.darken.sdmse.squeezer.core.tasks.SqueezerScanTask
import eu.darken.sdmse.squeezer.core.tasks.SqueezerTask
import eu.darken.sdmse.stats.core.Report
import eu.darken.sdmse.stats.core.ReportDetails
import eu.darken.sdmse.stats.core.SpaceHistoryRepo
import eu.darken.sdmse.stats.core.StatsRepo
import eu.darken.sdmse.stats.core.StatsSettings
import eu.darken.sdmse.stats.ui.AffectedFilesRoute
import eu.darken.sdmse.stats.ui.ReportsRoute
import eu.darken.sdmse.common.navigation.routes.SwiperSessionsRoute
import eu.darken.sdmse.systemcleaner.ui.FilterContentDetailsRoute
import eu.darken.sdmse.systemcleaner.ui.SystemCleanerListRoute
import eu.darken.sdmse.swiper.core.Swiper
import eu.darken.sdmse.systemcleaner.core.SystemCleaner
import eu.darken.sdmse.systemcleaner.core.hasData
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerOneClickTask
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerProcessingTask
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerScanTask
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerSchedulerTask
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    dispatcherProvider: DispatcherProvider,
    private val areaManager: DataAreaManager,
    internal val taskManager: TaskManager,
    internal val setupManager: SetupManager,
    internal val corpseFinder: CorpseFinder,
    internal val systemCleaner: SystemCleaner,
    internal val appCleaner: AppCleaner,
    internal val appControl: AppControl,
    internal val analyzer: Analyzer,
    debugCardProvider: DebugCardProvider,
    internal val deduplicator: Deduplicator,
    internal val squeezer: Squeezer,
    private val squeezerSettings: SqueezerSettings,
    swiper: Swiper,
    private val upgradeRepo: UpgradeRepo,
    private val generalSettings: GeneralSettings,
    internal val webpageTool: WebpageTool,
    schedulerManager: SchedulerManager,
    internal val updateService: UpdateService,
    private val sessionManager: DebugLogSessionManager,
    internal val motdRepo: MotdRepo,
    private val releaseManager: ReleaseManager,
    private val reviewTool: ReviewTool,
    anniversaryProvider: AnniversaryProvider,
    internal val statsRepo: StatsRepo,
    internal val statsSettings: StatsSettings,
    internal val spaceHistoryRepo: SpaceHistoryRepo,
) : ViewModel4(dispatcherProvider, TAG) {

    init {
        launch {
            releaseManager.checkEarlyAdopter()
        }
    }

    private val refreshTrigger = MutableStateFlow(rngString)

    val events = SingleEventFlow<DashboardEvents>()

    // The dashboard list is an all-or-nothing combine of many sources: it stays on the loading
    // spinner until every source has emitted once. Several sources re-run async work (Room, DataStore,
    // a filesystem scan) on every ViewModel recreation because they're shared with stopTimeout=0 /
    // replayExpiration=0, so a single slow source could wedge the whole screen permanently on a warm
    // Activity recreation. These helpers guarantee each combine input has an immediate first value and
    // tag it for diagnostics so a stalling upstream is identifiable from VERBOSE logs.
    private fun descLite(value: Any?): String = when (value) {
        null -> "null"
        is Collection<*> -> "size=${value.size}"
        else -> value::class.simpleName ?: "?"
    }

    /** Inject [fallback] as the immediate first value (outer onStart) while the inner handlers log the
     *  real upstream only — a missing "upstream emit" on reproduction pinpoints the staller. */
    private fun <T> Flow<T>.listStateSource(name: String, fallback: T): Flow<T> = this
        .onEach { log(TAG, VERBOSE) { "listState.$name upstream emit: ${descLite(it)}" } }
        .onStart { log(TAG, VERBOSE) { "listState.$name upstream start" } }
        .onStart { emit(fallback) }

    /** Diagnostic-only tag for sources that already have a guaranteed-immediate first value. */
    private fun <T> Flow<T>.listStateDiag(name: String): Flow<T> = this
        .onEach { log(TAG, VERBOSE) { "listState.$name upstream emit: ${descLite(it)}" } }
        .onStart { log(TAG, VERBOSE) { "listState.$name upstream start" } }

    private val updateInfo: Flow<UpdateDashboardCardItem?> = buildUpdateInfo()

    internal val upgradeInfo: Flow<UpgradeRepo.Info?> = upgradeRepo.upgradeInfo
        .map {
            @Suppress("USELESS_CAST")
            it as UpgradeRepo.Info?
        }
        // Emit null immediately so the dashboard's all-or-nothing combine (and every upgrade-gated
        // card + the bottom bar) never blocks waiting for upgrade state to resolve on a warm restart.
        // Consumers already null-guard (it?.isPro ?: false, nullable Info? in items).
        .onStart { emit(null) }
        .setupCommonEventHandlers(TAG) { "upgradeInfo" }
        .replayingShare(vmScope)

    internal val easterEggTriggered = MutableStateFlow(false)

    private val titleCardItem = buildTitleCardItem()

    private val corpseFinderItem: Flow<ToolDashboardCardItem> = buildCorpseFinderItem()

    private val systemCleanerItem: Flow<ToolDashboardCardItem> = buildSystemCleanerItem()

    private val appCleanerItem: Flow<ToolDashboardCardItem> = buildAppCleanerItem()

    private val deduplicatorItem: Flow<ToolDashboardCardItem?> = buildDeduplicatorItem()

    private val squeezerItem: Flow<SqueezerDashboardCardItem?> = buildSqueezerItem()

    private val appControlItem: Flow<AppControlDashboardCardItem?> = buildAppControlItem()

    private val analyzerItem: Flow<AnalyzerDashboardCardItem?> = buildAnalyzerItem()

    private val schedulerItem: Flow<SchedulerDashboardCardItem?> = combine(
        schedulerManager.state,
        taskManager.state,
    ) { schedulerState, taskState ->
        SchedulerDashboardCardItem(
            schedulerState = schedulerState,
            taskState = taskState,
            onManageClicked = {
                navTo(SchedulerManagerRoute)
            }
        )
    }

    private val dataAreaItem: Flow<ErrorDataAreaDashboardCardItem?> = areaManager.latestState
        .map {
            if (it == null) return@map null
            if (it.areas.isNotEmpty()) return@map null
            ErrorDataAreaDashboardCardItem(
                state = it,
                onReload = {
                    launch {
                        areaManager.reload()
                    }
                },
            )
        }

    private val motdItem: Flow<MotdDashboardCardItem?> = buildMotdItem()

    private val setupCardItem: Flow<SetupDashboardCardItem?> = buildSetupCardItem()

    private val reviewItem: Flow<ReviewDashboardCardItem?> = reviewTool.state.map { state ->
        if (!state.shouldAskForReview) return@map null

        ReviewDashboardCardItem(
            onReview = {
                launch { reviewTool.reviewNow(it) }
            },
            onDismiss = {
                launch { reviewTool.dismiss() }
            }
        )
    }

    private val statsItem: Flow<StatsDashboardCardItem?> = buildStatsItem()

    private val anniversaryItem: Flow<AnniversaryDashboardCardItem?> = anniversaryProvider.item

    private val swiperItem: Flow<SwiperDashboardCardItem?> = combine(
        swiper.getSessionsWithStats(),
        swiper.progress,
        upgradeInfo.map { it?.isPro ?: false },
    ) { sessionsWithStats, progress, isPro ->
        SwiperDashboardCardItem(
            sessionsWithStats = sessionsWithStats,
            progress = progress,
            showProRequirement = !isPro,
            onViewDetails = { showSwiper() }
        )
    }

    // Combine refresh trigger with card config to stay within combine's argument limit
    private val cardConfigWithRefresh: Flow<DashboardCardConfig> = combine(
        refreshTrigger,
        generalSettings.dashboardCardConfig.flow,
    ) { _, config -> config }

    val listState: StateFlow<ListState?> = eu.darken.sdmse.common.flow.combine(
        sessionManager.sessions.listStateSource("sessions", emptyList()),
        debugCardProvider.create(
            vm = this,
            onNavigate = { navTo(it as eu.darken.sdmse.common.navigation.NavigationDestination) },
            onError = { errorEvents.tryEmit(it) },
            onShowEvent = { events.tryEmit(it) },
        ).listStateSource("debug", null),
        titleCardItem.listStateDiag("title"),
        upgradeInfo.listStateDiag("upgrade"),
        updateInfo.listStateSource("update", null),
        setupCardItem.listStateSource("setup", null),
        dataAreaItem.listStateSource("dataArea", null),
        corpseFinderItem.listStateDiag("corpse"),
        systemCleanerItem.listStateDiag("system"),
        appCleanerItem.listStateDiag("app"),
        deduplicatorItem.listStateSource("dedup", null),
        squeezerItem.listStateSource("squeezer", null),
        appControlItem.listStateSource("appControl", null),
        analyzerItem.listStateDiag("analyzer"),
        schedulerItem.listStateSource("scheduler", null),
        motdItem.listStateSource("motd", null),
        reviewItem.listStateSource("review", null),
        anniversaryItem.listStateSource("anniversary", null),
        statsItem.listStateSource("stats", null),
        swiperItem.listStateSource("swiper", null),
        easterEggTriggered.listStateDiag("easterEgg"),
        cardConfigWithRefresh.listStateSource("cardConfig", DashboardCardConfig(cards = emptyList())),
    ) { sessions: List<DebugLogSession>,
        debugItem: DebugDashboardCardItem?,
        titleInfo: TitleDashboardCardItem,
        upgradeInfo: UpgradeRepo.Info?,
        updateInfo: UpdateDashboardCardItem?,
        setupItem: SetupDashboardCardItem?,
        dataAreaError: ErrorDataAreaDashboardCardItem?,
        corpseFinderItem: ToolDashboardCardItem?,
        systemCleanerItem: ToolDashboardCardItem?,
        appCleanerItem: ToolDashboardCardItem?,
        deduplicatorItem: ToolDashboardCardItem?,
        squeezerItem: SqueezerDashboardCardItem?,
        appControlItem: AppControlDashboardCardItem?,
        analyzerItem: AnalyzerDashboardCardItem?,
        schedulerItem: SchedulerDashboardCardItem?,
        motdItem: MotdDashboardCardItem?,
        reviewItem: ReviewDashboardCardItem?,
        anniversaryItem: AnniversaryDashboardCardItem?,
        statsItem: StatsDashboardCardItem?,
        swiperItem: SwiperDashboardCardItem?,
        easterEggTriggered,
        cardConfig: DashboardCardConfig ->
        val items = mutableListOf<DashboardItem>(titleInfo)

        val noError = dataAreaError == null

        val anyInitializing = setOfNotNull(
            corpseFinderItem?.isInitializing,
            systemCleanerItem?.isInitializing,
            appCleanerItem?.isInitializing,
            deduplicatorItem?.isInitializing,
            squeezerItem?.isInitializing,
            appControlItem?.isInitializing,
        ).any { it }

        if (motdItem == null && updateInfo == null && setupItem == null && noError && reviewItem != null && !anyInitializing) {
            log(TAG, INFO) { "Showing review item" }
            items.add(reviewItem)
        } else if (reviewItem != null) {
            log(TAG) { "Could show review item but other high priority items are currently being shown" }
        }

        motdItem?.let { items.add(it) }
        updateInfo?.let { items.add(it) }
        setupItem?.let { items.add(it) }
        dataAreaError?.let { items.add(it) }
        anniversaryItem?.let { items.add(it) }

        // Add tool cards based on user configuration
        for (entry in cardConfig.cards) {
            if (!entry.isVisible) continue
            when (entry.type) {
                DashboardCardType.CORPSEFINDER -> corpseFinderItem?.let { items.add(it) }
                DashboardCardType.SYSTEMCLEANER -> systemCleanerItem?.let { items.add(it) }
                DashboardCardType.APPCLEANER -> appCleanerItem?.let { items.add(it) }
                DashboardCardType.DEDUPLICATOR -> deduplicatorItem?.let { items.add(it) }
                DashboardCardType.SQUEEZER -> squeezerItem?.let { items.add(it) }
                DashboardCardType.APPCONTROL -> appControlItem?.let { items.add(it) }
                DashboardCardType.ANALYZER -> analyzerItem?.let { items.add(it) }
                DashboardCardType.SCHEDULER -> schedulerItem?.let { items.add(it) }
                DashboardCardType.SWIPER -> swiperItem?.let { items.add(it) }
                DashboardCardType.STATS -> statsItem?.let { items.add(it) }
            }
        }

        upgradeInfo
            ?.takeIf { !it.isPro }
            ?.let {
                UpgradeDashboardCardItem(
                    onUpgrade = { navTo(UpgradeRoute()) }
                )
            }
            ?.run { items.add(this) }

        val recordingSession = sessions.filterIsInstance<DebugLogSession.Recording>().firstOrNull()
        val isRecording = recordingSession != null
        if (isRecording || debugItem != null) {
            val item = DebugRecorderDashboardCardItem(
                webpageTool = webpageTool,
                isRecording = isRecording,
                currentLogDir = recordingSession?.logDir,
                onToggleRecording = {
                    if (isRecording) {
                        launch {
                            when (val result = sessionManager.requestStopRecording()) {
                                is RecorderModule.StopResult.TooShort -> {
                                    events.tryEmit(DashboardEvents.ShowShortRecordingWarning)
                                }
                                is RecorderModule.StopResult.Stopped -> {
                                    launchRecorderActivity(result.sessionId)
                                }
                                is RecorderModule.StopResult.NotRecording -> {}
                            }
                        }
                    } else {
                        launch { sessionManager.startRecording() }
                    }
                }
            )
            items.add(item)
        }

        debugItem?.let { items.add(it) }

        ListState(
            items = items,
            isEasterEgg = easterEggTriggered,
        )
    }
        .throttleLatest(500)
        .safeStateIn(
            initialValue = null,
            onError = { null },
        )

    data class ListState(
        val items: List<DashboardItem>,
        val isEasterEgg: Boolean = false,
    )

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
     * main action ([mainAction] with [BottomBarState.Action.DELETE]) will free: each tool is
     * included only when its one-click toggle is enabled, it has data, and — for AppCleaner — the
     * user is Pro. Deduplicator contributes its freeable [Deduplicator.Data.redundantSize] and a
     * cluster count (kept out of [itemCount], which counts discrete files only).
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

    val oneClickOptionsState: StateFlow<OneClickOptionsState> = combine(
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
    }.safeStateIn(
        initialValue = OneClickOptionsState(),
        onError = { OneClickOptionsState() },
    )

    /** Aggregated "freed" result of the most recent main-action deletion/one-click; null otherwise. */
    private val freedResult = MutableStateFlow<HeroSummary?>(null)

    /**
     * Batch start of the current cleanup, stamped in [mainAction] before any branch runs. Used to
     * resolve each freed-hero chip to *this* cleanup's per-tool report (reports completed at/after
     * this instant), instead of a stale earlier one. [Instant.EPOCH] until the first cleanup.
     */
    private var freedResultSince: Instant = Instant.EPOCH

    /** In-flight main-action cleanup branches; the freed hero stays hidden until this reaches 0. */
    private val pendingMainCleanup = MutableStateFlow(0)

    /** While [discardResults] clears the tools one by one, suppress the hero so it never shows partial data. */
    private val discarding = MutableStateFlow(false)

    private val nowTicks: Flow<Instant> = intervalFlow(1.minutes).map { Instant.now() }

    val bottomBarState: StateFlow<BottomBarState?> = eu.darken.sdmse.common.flow.combine(
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
        listState.map { state -> state?.items?.any { it is MainActionItem } == true },
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
        listIsReady,
        isDiscarding,
        now ->

        val actionState: BottomBarState.Action = when {
            taskState.hasCancellable -> BottomBarState.Action.WORKING_CANCELABLE
            !taskState.isIdle -> BottomBarState.Action.WORKING
            // Deduplicator is intentionally excluded from the DELETE trigger (it has its own
            // cluster-selection delete flow); a dedupe-only result therefore won't summon the hero.
            corpseState.data.hasData || filterState.data.hasData || junkState.data.hasData -> BottomBarState.Action.DELETE
            oneClickMode -> BottomBarState.Action.ONECLICK
            else -> BottomBarState.Action.SCAN
        }
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
            isReady = listIsReady,
            actionState = actionState,
            activeTasks = activeTasks,
            queuedTasks = queuedTasks,
            heroSummary = heroSummary,
            upgradeInfo = upgradeInfo,
            now = now,
        )
    }.safeStateIn(
        initialValue = null,
        onError = { null },
    )

    private val heroDismissed = MutableStateFlow(false)

    /** Whether the user dismissed the hero for the current results. In-memory; resets on a fresh scan. */
    val isHeroDismissed: StateFlow<Boolean> = heroDismissed

    init {
        // A freshly completed *scan* clears any stale "freed" result and revives a dismissed hero.
        // We must only react to a *strictly newer* scan time: TaskManager keeps one task per tool,
        // so a delete prunes that tool's scan result and would otherwise make this "change" to an
        // older/absent scan time and wrongly clear the freed hero we just produced.
        launch {
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
        launch {
            freedResult
                .map { it != null }
                .distinctUntilChanged()
                .collect { hasFreed -> if (hasFreed) heroDismissed.value = false }
        }
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
    fun discardResults() = launch {
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
            heroDismissed.value = false
        } finally {
            discarding.value = false
        }
    }

    fun setCorpseFinderOneClickEnabled(enabled: Boolean) = launch {
        generalSettings.oneClickCorpseFinderEnabled.value(enabled)
    }

    fun setSystemCleanerOneClickEnabled(enabled: Boolean) = launch {
        generalSettings.oneClickSystemCleanerEnabled.value(enabled)
    }

    fun setAppCleanerOneClickEnabled(enabled: Boolean) = launch {
        generalSettings.oneClickAppCleanerEnabled.value(enabled)
    }

    fun setDeduplicatorOneClickEnabled(enabled: Boolean) = launch {
        generalSettings.oneClickDeduplicatorEnabled.value(enabled)
    }

    // Runs one main-action tool branch and, for cleanups, decrements the pending counter when it
    // settles — so the freed hero only appears once *all* branches are done (no partial flash).
    private fun launchMainBranch(isCleanup: Boolean, block: suspend () -> Unit) = launch {
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
                    if (appCleaner.state.first().data != null && upgradeRepo.isPro()) {
                        accumulateFreed(SDMTool.Type.APPCLEANER, submitTask(AppCleanerProcessingTask()))
                    } else if (appCleaner.state.first().data.hasData && !corpseFinder.state.first().data.hasData && !systemCleaner.state.first().data.hasData) {
                        navTo(UpgradeRoute())
                    }
                }

                BottomBarState.Action.ONECLICK -> {
                    if (upgradeRepo.isPro()) {
                        accumulateFreed(SDMTool.Type.APPCLEANER, submitTask(AppCleanerOneClickTask()))
                    } else if (appCleaner.state.first().data.hasData && !corpseFinder.state.first().data.hasData && !systemCleaner.state.first().data.hasData) {
                        navTo(UpgradeRoute())
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
                BottomBarState.Action.DELETE -> if (deduplicator.state.first().data != null && upgradeRepo.isPro()) {
                    accumulateFreed(SDMTool.Type.DEDUPLICATOR, submitTask(DeduplicatorDeleteTask()))
                }

                BottomBarState.Action.ONECLICK -> {
                    if (upgradeRepo.isPro()) {
                        accumulateFreed(SDMTool.Type.DEDUPLICATOR, submitTask(DeduplicatorOneClickTask()))
                    } else if (deduplicator.state.first().data.hasData && !corpseFinder.state.first().data.hasData && !systemCleaner.state.first().data.hasData) {
                        navTo(UpgradeRoute())
                    }
                }
            }
        }
    }

    fun confirmCorpseDeletion() = launch {
        log(TAG, INFO) { "confirmCorpseDeletion()" }
        submitTask(CorpseFinderDeleteTask())
    }

    fun showCorpseFinder() {
        log(TAG, INFO) { "showCorpseFinderDetails()" }
        navTo(CorpseFinderListRoute)
    }

    fun confirmFilterContentDeletion() = launch {
        log(TAG, INFO) { "confirmFilterContentDeletion()" }
        submitTask(SystemCleanerProcessingTask())
    }

    fun showSystemCleaner() {
        log(TAG, INFO) { "showSystemCleanerDetails()" }
        navTo(SystemCleanerListRoute)
    }

    fun confirmAppJunkDeletion() = launch {
        log(TAG, INFO) { "confirmAppJunkDeletion()" }

        if (!upgradeRepo.isPro()) {
            navTo(UpgradeRoute())
            return@launch
        }
        submitTask(AppCleanerProcessingTask())
    }

    fun showAppCleaner() {
        log(TAG, INFO) { "showAppCleanerDetails()" }
        navTo(AppCleanerListRoute)
    }

    fun showDeduplicator() {
        log(TAG, INFO) { "showDeduplicatorDetails()" }
        navTo(DeduplicatorListRoute)
    }

    /**
     * Hero-chip tap. Routes by the *rendered* card mode (passed up from the card, not re-read from
     * state): a "will be freed" chip opens the live findings list to review; a "freed" chip opens
     * what that tool actually removed (its report), since the live list is now empty.
     */
    fun onHeroToolClick(mode: HeroSummary.Mode, type: SDMTool.Type) {
        log(TAG, INFO) { "onHeroToolClick($mode, $type)" }
        when (mode) {
            HeroSummary.Mode.FREED -> showToolReport(type)
            HeroSummary.Mode.FREEABLE -> showTool(type)
        }
    }

    /** Opens a tool's findings list — used by the hero card's per-tool chips (pre-deletion). */
    fun showTool(type: SDMTool.Type) {
        log(TAG, INFO) { "showTool($type)" }
        when (type) {
            SDMTool.Type.CORPSEFINDER -> showCorpseFinder()
            SDMTool.Type.SYSTEMCLEANER -> showSystemCleaner()
            SDMTool.Type.APPCLEANER -> showAppCleaner()
            SDMTool.Type.DEDUPLICATOR -> showDeduplicator()
            else -> log(TAG, WARN) { "showTool() ignoring unsupported type: $type" }
        }
    }

    /**
     * Opens the report for what [type] just removed (post-deletion freed-hero chip). Resolves *this*
     * cleanup's report via [freedResultSince]; falls back to the reports list when the report isn't
     * persisted yet (sub-second tap) or the deletion failed.
     */
    fun showToolReport(type: SDMTool.Type) = launch {
        log(TAG, INFO) { "showToolReport($type)" }
        val report = statsRepo.getReportForToolSince(type, freedResultSince)
        when (report?.status) {
            Report.Status.SUCCESS, Report.Status.PARTIAL_SUCCESS -> navTo(AffectedFilesRoute(report.reportId))
            else -> navTo(ReportsRoute)
        }
    }

    fun showSwiper() {
        log(TAG, INFO) { "showSwiper()" }
        navTo(SwiperSessionsRoute)
    }

    fun confirmDeduplicatorDeletion() = launch {
        log(TAG, INFO) { "confirmDeduplicatorDeletion()" }

        if (!upgradeRepo.isPro()) {
            navTo(UpgradeRoute())
            return@launch
        }
        submitTask(DeduplicatorDeleteTask())
    }

    fun showSqueezer() {
        log(TAG, INFO) { "showSqueezerDetails()" }
        navTo(SqueezerListRoute)
    }

    fun confirmStopRecording() = launch {
        val result = sessionManager.forceStopRecording() ?: return@launch
        launchRecorderActivity(result.sessionId)
    }

    private fun launchRecorderActivity(sessionId: SessionId) {
        val intent = RecorderActivity.getLaunchIntent(context, sessionId).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        events.tryEmit(DashboardEvents.OpenIntent(intent))
    }

    fun undoSetupHide() = launch {
        log(TAG) { "undoSetupHide()" }
        setupManager.setDismissed(false)
    }

    internal suspend fun submitTask(task: SDMTool.Task): SDMTool.Task.Result {
        log(TAG, VERBOSE) { "Submitting $task" }
        val result = taskManager.submit(task)
        log(TAG, VERBOSE) { "Task result for $task was $result" }
        when (result) {
            is CorpseFinderTask.Result -> when (result) {
                is CorpseFinderScanTask.Success -> {}
                is UninstallWatcherTask.Success -> {}
                is CorpseFinderSchedulerTask.Success -> {}
                is CorpseFinderDeleteTask.Success -> events.tryEmit(DashboardEvents.TaskResult(result))
                is CorpseFinderOneClickTask.Success -> events.tryEmit(DashboardEvents.TaskResult(result))
            }

            is SystemCleanerTask.Result -> when (result) {
                is SystemCleanerScanTask.Success -> {}
                is SystemCleanerSchedulerTask.Success -> {}
                is SystemCleanerProcessingTask.Success -> events.tryEmit(DashboardEvents.TaskResult(result))
                is SystemCleanerOneClickTask.Success -> events.tryEmit(DashboardEvents.TaskResult(result))
            }

            is AppCleanerTask.Result -> when (result) {
                is AppCleanerScanTask.Success -> {}
                is AppCleanerSchedulerTask.Success -> {}
                is AppCleanerProcessingTask.Success -> events.tryEmit(DashboardEvents.TaskResult(result))
                is AppCleanerOneClickTask.Success -> events.tryEmit(DashboardEvents.TaskResult(result))
            }

            is DeduplicatorTask.Result -> when (result) {
                is DeduplicatorScanTask.Success -> {}
                is DeduplicatorDeleteTask.Success -> events.tryEmit(DashboardEvents.TaskResult(result))
                is DeduplicatorOneClickTask.Success -> events.tryEmit(DashboardEvents.TaskResult(result))
            }

            is SqueezerTask.Result -> when (result) {
                is SqueezerScanTask.Success -> {}
                is SqueezerProcessTask.Success -> events.tryEmit(DashboardEvents.TaskResult(result))
            }
        }
        return result
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
                itemCount = slices.filter { it.type != SDMTool.Type.DEDUPLICATOR }.sumOf { it.count },
                tools = slices,
                timestamp = Instant.now(),
            )
        }
    }

    companion object {
        private val TAG = logTag("Dashboard", "ViewModel")

        /**
         * Builds the action-truthful hero summary: only tools the main DELETE action will actually
         * free (one-click toggle on, has data, AppCleaner additionally requires Pro). Returns null
         * when nothing is one-tap-actionable for this user/config, even if raw scan data exists.
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
                dedupe?.takeIf { oneClick.deduplicatorEnabled && it.hasData }?.let {
                    add(HeroSummary.ToolSlice(SDMTool.Type.DEDUPLICATOR, it.redundantSize, it.clusters.size))
                }
            }
            if (tools.isEmpty()) return null
            return HeroSummary(
                mode = HeroSummary.Mode.FREEABLE,
                totalSize = tools.sumOf { it.size },
                // Deduplicator's unit is clusters, not discrete files — keep it out of the item headline.
                itemCount = tools.filter { it.type != SDMTool.Type.DEDUPLICATOR }.sumOf { it.count },
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
 * hero on fresh scans and to stamp [DashboardViewModel.HeroSummary.timestamp].
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
