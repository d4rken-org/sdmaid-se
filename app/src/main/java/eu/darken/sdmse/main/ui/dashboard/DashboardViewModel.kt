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
import eu.darken.sdmse.common.device.DeviceDetective
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
import eu.darken.sdmse.squeezer.ui.SqueezerSetupRoute
import eu.darken.sdmse.squeezer.core.tasks.SqueezerProcessTask
import eu.darken.sdmse.squeezer.core.tasks.SqueezerScanTask
import eu.darken.sdmse.squeezer.core.tasks.SqueezerTask
import eu.darken.sdmse.stats.core.Report
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
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
    deviceDetective: DeviceDetective,
) : ViewModel4(dispatcherProvider, TAG) {

    // TV-style devices navigate via D-pad focus, which scrolls the grid — auto-hiding dock on
    // scroll would hide controls the user is about to focus. Static per process, no flow needed.
    val isTvDevice: Boolean by lazy { deviceDetective.isTvLikeDevice() }

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
        // Self-seed null so the card is present (as an initializing placeholder) in the very first
        // skeleton frame instead of popping in after schedulerManager.state (DynamicStateFlow on IO)
        // resolves. null renders a neutral loading card, not a misleading "no active schedules".
        (schedulerManager.state as Flow<SchedulerManager.State?>).onStart { emit(null) },
        taskManager.state,
    ) { schedulerState, taskState ->
        SchedulerDashboardCardItem(
            isInitializing = schedulerState == null,
            schedulerState = schedulerState ?: SchedulerManager.State(schedules = emptySet()),
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
        // Self-seed null so the card is present (as an initializing placeholder) in the very first
        // skeleton frame instead of popping in after the Room-backed sessions query resolves.
        (swiper.getSessionsWithStats() as Flow<List<Swiper.SessionWithStats>?>).onStart { emit(null) },
        swiper.progress.onStart { emit(null) },
    ) { sessionsWithStats, progress ->
        SwiperDashboardCardItem(
            isInitializing = sessionsWithStats == null,
            sessionsWithStats = sessionsWithStats ?: emptyList(),
            progress = progress,
            onViewDetails = { showSwiper() }
        )
    }

    private val dashboardCardConfig: Flow<DashboardCardConfig> = generalSettings.dashboardCardConfig.flow
        .replayingShare(vmScope)

    // Combine refresh trigger with card config to stay within combine's argument limit
    private val cardConfigWithRefresh: Flow<DashboardCardConfig> = combine(
        refreshTrigger,
        dashboardCardConfig,
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
        deduplicatorItem.listStateDiag("dedup"),
        squeezerItem.listStateDiag("squeezer"),
        appControlItem.listStateDiag("appControl"),
        analyzerItem.listStateDiag("analyzer"),
        schedulerItem.listStateDiag("scheduler"),
        motdItem.listStateSource("motd", null),
        reviewItem.listStateSource("review", null),
        anniversaryItem.listStateSource("anniversary", null),
        statsItem.listStateSource("stats", null),
        swiperItem.listStateDiag("swiper"),
        easterEggTriggered.listStateDiag("easterEgg"),
        cardConfigWithRefresh.listStateDiag("cardConfig"),
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

    /** The main-action/hero state machine; this VM delegates to it and keeps error/navigation routing. */
    private val mainActionEngine = DashboardMainActionEngine(
        scope = vmScope,
        taskManager = taskManager,
        corpseFinder = corpseFinder,
        systemCleaner = systemCleaner,
        appCleaner = appCleaner,
        deduplicator = deduplicator,
        generalSettings = generalSettings,
        upgradeRepo = upgradeRepo,
        upgradeInfo = upgradeInfo,
        submitTask = ::submitTask,
        onUpgradeRequired = { navTo(UpgradeRoute()) },
    )

    val oneClickOptionsState: StateFlow<OneClickOptionsState> = mainActionEngine.oneClickOptions.safeStateIn(
        initialValue = OneClickOptionsState(),
        onError = { OneClickOptionsState() },
    )

    val bottomBarState: StateFlow<BottomBarState?> = mainActionEngine.bottomBarState(
        listIsReady = listState.map { state -> state?.items?.any { it is MainActionItem } == true },
        oneClickOptionsState = oneClickOptionsState,
    ).safeStateIn(
        initialValue = null,
        onError = { null },
    )

    /** Whether the user dismissed the hero for the current results. In-memory; resets on a fresh scan. */
    val isHeroDismissed: StateFlow<Boolean> = mainActionEngine.isHeroDismissed

    fun dismissHero() = mainActionEngine.dismissHero()

    /** Re-shows a hero the user dismissed (via the compact summary chip in the bar). */
    fun restoreHero() = mainActionEngine.restoreHero()

    /**
     * Drops all pending scan results, returning the dashboard to its pristine SCAN state. Unlike
     * [dismissHero] (which only hides the card), this clears the tools' data, so the main action
     * no longer threatens deletion. Recoverable by simply rescanning, hence no confirmation step.
     */
    fun discardResults() = mainActionEngine.discardResults()

    fun setCorpseFinderOneClickEnabled(enabled: Boolean) = mainActionEngine.setCorpseFinderOneClickEnabled(enabled)

    fun setSystemCleanerOneClickEnabled(enabled: Boolean) = mainActionEngine.setSystemCleanerOneClickEnabled(enabled)

    fun setAppCleanerOneClickEnabled(enabled: Boolean) = mainActionEngine.setAppCleanerOneClickEnabled(enabled)

    fun setDeduplicatorOneClickEnabled(enabled: Boolean) = mainActionEngine.setDeduplicatorOneClickEnabled(enabled)

    fun mainAction(actionState: BottomBarState.Action) = mainActionEngine.mainAction(actionState)

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
     * cleanup's report via [DashboardMainActionEngine.freedResultSince]; falls back to the reports
     * list when the report isn't persisted yet (sub-second tap) or the deletion failed.
     */
    fun showToolReport(type: SDMTool.Type) = launch {
        log(TAG, INFO) { "showToolReport($type)" }
        val report = statsRepo.getReportForToolSince(type, mainActionEngine.freedResultSince)
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

    companion object {
        private val TAG = logTag("Dashboard", "ViewModel")
    }
}
