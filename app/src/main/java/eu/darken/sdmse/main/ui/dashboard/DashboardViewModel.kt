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
import eu.darken.sdmse.common.navigation.routes.LogViewRoute
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
import eu.darken.sdmse.stats.core.SpaceHistoryRepo
import eu.darken.sdmse.stats.core.StatsRepo
import eu.darken.sdmse.stats.core.StatsSettings
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
import kotlinx.coroutines.flow.onStart
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    dispatcherProvider: DispatcherProvider,
    private val areaManager: DataAreaManager,
    private val taskManager: TaskManager,
    private val setupManager: SetupManager,
    private val corpseFinder: CorpseFinder,
    private val systemCleaner: SystemCleaner,
    private val appCleaner: AppCleaner,
    appControl: AppControl,
    analyzer: Analyzer,
    debugCardProvider: DebugCardProvider,
    private val deduplicator: Deduplicator,
    private val squeezer: Squeezer,
    private val squeezerSettings: SqueezerSettings,
    swiper: Swiper,
    private val upgradeRepo: UpgradeRepo,
    private val generalSettings: GeneralSettings,
    private val webpageTool: WebpageTool,
    schedulerManager: SchedulerManager,
    private val updateService: UpdateService,
    private val sessionManager: DebugLogSessionManager,
    private val motdRepo: MotdRepo,
    private val releaseManager: ReleaseManager,
    private val reviewTool: ReviewTool,
    anniversaryProvider: AnniversaryProvider,
    statsRepo: StatsRepo,
    private val statsSettings: StatsSettings,
    private val spaceHistoryRepo: SpaceHistoryRepo,
) : ViewModel4(dispatcherProvider, TAG) {

    init {
        launch {
            releaseManager.checkEarlyAdopter()
        }
    }

    private val refreshTrigger = MutableStateFlow(rngString)

    val events = SingleEventFlow<DashboardEvents>()

    private val updateInfo: Flow<UpdateDashboardCardItem?> = updateService.availableUpdate
        .map { update ->
            if (update == null) {
                log(TAG, INFO) { "No update available" }
                return@map null
            }

            try {
                return@map UpdateDashboardCardItem(
                    update = update,
                    onDismiss = {
                        launch {
                            updateService.dismissUpdate(update)
                            updateService.refresh()
                        }
                    },
                    onViewUpdate = {
                        launch { updateService.viewUpdate(update) }
                    },
                    onUpdate = {
                        launch { updateService.startUpdate(update) }
                    }
                )
            } catch (e: Exception) {
                log(TAG, WARN) { "Update check failed: ${e.asLog()}" }
                return@map null
            }
        }
        .onStart { emit(null) }

    private val upgradeInfo: Flow<UpgradeRepo.Info?> = upgradeRepo.upgradeInfo
        .map {
            @Suppress("USELESS_CAST")
            it as UpgradeRepo.Info?
        }
        .setupCommonEventHandlers(TAG) { "upgradeInfo" }
        .replayingShare(vmScope)

    private val easterEggTriggered = MutableStateFlow(false)

    private val titleCardItem = combine(
        upgradeInfo,
        taskManager.state,
    ) { upgradeInfo, taskState ->
        TitleDashboardCardItem(
            upgradeInfo = upgradeInfo,
            webpageTool = webpageTool,
            isWorking = !taskState.isIdle,
            onRibbonClicked = { webpageTool.open(SdmSeLinks.ISSUES) },
            onMascotTriggered = { easterEggTriggered.value = it }
        )
    }

    private val corpseFinderItem: Flow<ToolDashboardCardItem> = combine(
        (corpseFinder.state as Flow<CorpseFinder.State?>).onStart { emit(null) },
        taskManager.state.map { it.getLatestResult(SDMTool.Type.CORPSEFINDER) },
    ) { state, lastResult ->
        ToolDashboardCardItem(
            toolType = SDMTool.Type.CORPSEFINDER,
            isInitializing = state == null,
            result = lastResult,
            progress = state?.progress,
            showProRequirement = false,
            onScan = {
                launch { submitTask(CorpseFinderScanTask()) }
            },
            onDelete = {
                events.tryEmit(DashboardEvents.CorpseFinderDeleteConfirmation(CorpseFinderDeleteTask()))
                Unit
            }.takeIf { state?.data?.hasData == true },
            onCancel = {
                launch { taskManager.cancel(SDMTool.Type.CORPSEFINDER) }
            },
            onViewTool = { showCorpseFinder() },
            onViewDetails = {
                navTo(CorpseDetailsRoute())
            },
        )
    }

    private val systemCleanerItem: Flow<ToolDashboardCardItem> = combine(
        (systemCleaner.state as Flow<SystemCleaner.State?>).onStart { emit(null) },
        taskManager.state.map { it.getLatestResult(SDMTool.Type.SYSTEMCLEANER) },
    ) { state, lastResult ->
        ToolDashboardCardItem(
            toolType = SDMTool.Type.SYSTEMCLEANER,
            isInitializing = state == null,
            result = lastResult,
            progress = state?.progress,
            showProRequirement = false,
            onScan = {
                launch { submitTask(SystemCleanerScanTask()) }
            },
            onDelete = {
                events.tryEmit(DashboardEvents.SystemCleanerDeleteConfirmation(SystemCleanerProcessingTask()))
                Unit
            }.takeIf { state?.data?.hasData == true },
            onCancel = {
                launch { taskManager.cancel(SDMTool.Type.SYSTEMCLEANER) }
            },
            onViewTool = { showSystemCleaner() },
            onViewDetails = {
                navTo(FilterContentDetailsRoute())
            },
        )
    }

    private val appCleanerItem: Flow<ToolDashboardCardItem> = combine(
        (appCleaner.state as Flow<AppCleaner.State?>).onStart { emit(null) },
        taskManager.state.map { it.getLatestResult(SDMTool.Type.APPCLEANER) },
        upgradeInfo.map { it?.isPro ?: false },
    ) { state, lastResult, isPro ->
        ToolDashboardCardItem(
            toolType = SDMTool.Type.APPCLEANER,
            isInitializing = state == null,
            result = lastResult,
            progress = state?.progress,
            showProRequirement = !isPro,
            onScan = {
                launch { submitTask(AppCleanerScanTask()) }
            },
            onDelete = {
                events.tryEmit(DashboardEvents.AppCleanerDeleteConfirmation(AppCleanerProcessingTask()))
                Unit
            }.takeIf { state?.data?.hasData == true },
            onCancel = {
                launch { taskManager.cancel(SDMTool.Type.APPCLEANER) }
            },
            onViewTool = { showAppCleaner() },
            onViewDetails = {
                navTo(AppJunkDetailsRoute())
            },
        )
    }

    private val deduplicatorItem: Flow<ToolDashboardCardItem?> = combine(
        (deduplicator.state as Flow<Deduplicator.State?>).onStart { emit(null) },
        taskManager.state.map { it.getLatestResult(SDMTool.Type.DEDUPLICATOR) },
        upgradeInfo.map { it?.isPro ?: false },
    ) { state, lastResult, isPro ->
        ToolDashboardCardItem(
            toolType = SDMTool.Type.DEDUPLICATOR,
            isInitializing = state == null,
            result = lastResult,
            progress = state?.progress,
            showProRequirement = !isPro,
            onScan = {
                launch { submitTask(DeduplicatorScanTask()) }
            },
            onDelete = {
                launch {
                    val event = DashboardEvents.DeduplicatorDeleteConfirmation(
                        task = DeduplicatorDeleteTask(),
                        clusters = deduplicator.state.first().data?.clusters?.sortedByDescending { it.averageSize }
                    )
                    events.tryEmit(event)
                }
            }.takeIf { state?.data?.hasData == true },
            onCancel = {
                launch { taskManager.cancel(SDMTool.Type.DEDUPLICATOR) }
            },
            onViewTool = { showDeduplicator() },
            onViewDetails = {
                navTo(DeduplicatorDetailsRoute())
            },
        )
    }

    private val squeezerItem: Flow<SqueezerDashboardCardItem?> = (squeezer.state as Flow<Squeezer.State?>)
        .onStart { emit(null) }
        .mapLatest { state ->
            SqueezerDashboardCardItem(
                isInitializing = state == null,
                isNew = true,
                data = state?.data,
                progress = state?.progress,
                onViewDetails = {
                    navTo(SqueezerSetupRoute)
                },
            )
        }

    private val appControlItem: Flow<AppControlDashboardCardItem?> = (appControl.state as Flow<AppControl.State?>)
        .onStart { emit(null) }
        .mapLatest { state ->
            AppControlDashboardCardItem(
                isInitializing = state == null,
                data = state?.data,
                progress = state?.progress,
                onViewDetails = {
                    navTo(AppControlListRoute)
                }
            )
        }

    private val analyzerItem: Flow<AnalyzerDashboardCardItem?> = combine(
        analyzer.data,
        analyzer.progress,
        intervalFlow(1.hours).flatMapLatest {
            spaceHistoryRepo.getAllHistory(Instant.now() - Duration.ofDays(7))
        },
    ) { data, progress, snapshots ->
        val combinedDelta = snapshots
            .groupBy { it.storageId }
            .values
            .filter { it.size >= 2 }
            .sumOf { group ->
                val sorted = group.sortedBy { it.recordedAt }
                val firstUsed = sorted.first().let { it.spaceCapacity - it.spaceFree }
                val lastUsed = sorted.last().let { it.spaceCapacity - it.spaceFree }
                lastUsed - firstUsed
            }
            .takeIf { snapshots.groupBy { s -> s.storageId }.values.any { it.size >= 2 } }

        AnalyzerDashboardCardItem(
            data = data,
            progress = progress,
            combinedDelta = combinedDelta,
            onViewDetails = {
                navTo(DeviceStorageRoute)
            },
        )
    }

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

    private val motdItem: Flow<MotdDashboardCardItem?> = motdRepo.motd
        .map {
            if (it == null) return@map null
            MotdDashboardCardItem(
                state = it,
                onPrimary = {
                    launch {
                        it.motd.primaryLink?.let { webpageTool.open(it) }
                    }
                },
                onTranslate = {
                    launch {
                        webpageTool.open(it.translationUrl)
                    }
                },
                onDismiss = {
                    launch { motdRepo.dismiss(it.id) }
                }
            )
        }

    private val setupCardItem: Flow<SetupDashboardCardItem?> = setupManager.state
        .flatMapLatest { setupState ->
            if (setupState.isDone || setupState.isDismissed) return@flatMapLatest flowOf(null)

            val item = SetupDashboardCardItem(
                setupState = setupState,
                onDismiss = {
                    setupManager.setDismissed(true)
                    events.tryEmit(DashboardEvents.SetupDismissHint)
                },
                onContinue = { navTo(SetupRoute()) }
            )

            if (setupState.isIncomplete) return@flatMapLatest flowOf(item)

            if (!setupState.isLoading) return@flatMapLatest flowOf(null)

            intervalFlow(1.seconds).map {
                val now = Instant.now()
                val loadingStart = setupState.startedLoadingAt ?: now
                if (Duration.between(loadingStart, now) >= Duration.ofSeconds(5)) {
                    item
                } else {
                    null
                }
            }
        }

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

    private val statsItem: Flow<StatsDashboardCardItem?> = combine(
        statsRepo.state,
        upgradeInfo.map { it?.isPro ?: false },
        statsSettings.retentionReports.flow,
        statsSettings.retentionPaths.flow,
        statsSettings.retentionSnapshots.flow,
    ) { state, isPro, retentionReports, retentionPaths, retentionSnapshots ->
        // Hide card if all retention settings are disabled
        if (retentionReports == Duration.ZERO && retentionPaths == Duration.ZERO && retentionSnapshots == Duration.ZERO) {
            return@combine null
        }
        // Also hide if there's no data
        if (state.isEmpty) return@combine null
        StatsDashboardCardItem(
            state = state,
            showProRequirement = !isPro,
            onViewAction = {
                if (isPro) {
                    navTo(ReportsRoute)
                } else {
                    navTo(UpgradeRoute())
                }
            }
        )
    }

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
        sessionManager.sessions,
        debugCardProvider.create(
            vm = this,
            onNavigate = { navTo(it as eu.darken.sdmse.common.navigation.NavigationDestination) },
            onError = { errorEvents.tryEmit(it) },
            onShowEvent = { events.tryEmit(it) },
        ),
        titleCardItem,
        upgradeInfo,
        updateInfo,
        setupCardItem,
        dataAreaItem,
        corpseFinderItem,
        systemCleanerItem,
        appCleanerItem,
        deduplicatorItem,
        squeezerItem,
        appControlItem,
        analyzerItem,
        schedulerItem,
        motdItem,
        reviewItem,
        anniversaryItem,
        statsItem,
        swiperItem,
        easterEggTriggered,
        cardConfigWithRefresh,
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
            if (isRecording) items.add(1, item) else items.add(item)
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
        val totalItems: Int,
        val totalSize: Long,
        val upgradeInfo: UpgradeRepo.Info?,
    ) {
        enum class Action {
            SCAN,
            DELETE,
            ONECLICK,
            WORKING,
            WORKING_CANCELABLE
        }
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

    val bottomBarState: StateFlow<BottomBarState?> = eu.darken.sdmse.common.flow.combine(
        upgradeInfo,
        taskManager.state,
        corpseFinder.state,
        systemCleaner.state,
        appCleaner.state,
        generalSettings.enableDashboardOneClick.flow,
        listState.map { state -> state?.items?.any { it is MainActionItem } == true },
    ) { upgradeInfo,
        taskState,
        corpseState,
        filterState,
        junkState,
        oneClickMode,
        listIsReady ->

        val actionState: BottomBarState.Action = when {
            taskState.hasCancellable -> BottomBarState.Action.WORKING_CANCELABLE
            !taskState.isIdle -> BottomBarState.Action.WORKING
            corpseState.data.hasData || filterState.data.hasData || junkState.data.hasData -> BottomBarState.Action.DELETE
            oneClickMode -> BottomBarState.Action.ONECLICK
            else -> BottomBarState.Action.SCAN
        }
        val activeTasks = taskState.tasks.filter { it.isActive }.size
        val queuedTasks = taskState.tasks.filter { it.isQueued }.size
        val totalItems =
            (corpseState.data?.totalCount ?: 0) + (filterState.data?.totalCount ?: 0) + (junkState.data?.totalCount
                ?: 0)
        val totalSize =
            (corpseState.data?.totalSize ?: 0L) + (filterState.data?.totalSize ?: 0L) + (junkState.data?.totalSize
                ?: 0L)
        BottomBarState(
            isReady = listIsReady,
            actionState = actionState,
            activeTasks = activeTasks,
            queuedTasks = queuedTasks,
            totalItems = totalItems,
            totalSize = totalSize,
            upgradeInfo = upgradeInfo,
        )
    }.safeStateIn(
        initialValue = null,
        onError = { null },
    )

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

    fun mainAction(actionState: BottomBarState.Action) {
        log(TAG) { "mainAction(actionState=$actionState)" }
        launch {
            if (!generalSettings.oneClickCorpseFinderEnabled.value()) {
                log(VERBOSE) { "CorpseFinder is disabled one-click mode." }
                return@launch
            }

            when (actionState) {
                BottomBarState.Action.SCAN -> submitTask(CorpseFinderScanTask())
                BottomBarState.Action.WORKING_CANCELABLE -> taskManager.cancel(SDMTool.Type.CORPSEFINDER)
                BottomBarState.Action.WORKING -> {}
                BottomBarState.Action.DELETE -> if (corpseFinder.state.first().data != null) {
                    submitTask(CorpseFinderDeleteTask())
                }

                BottomBarState.Action.ONECLICK -> submitTask(CorpseFinderOneClickTask())
            }
        }
        launch {
            if (!generalSettings.oneClickSystemCleanerEnabled.value()) {
                log(VERBOSE) { "SystemCleaner is disabled one-click mode." }
                return@launch
            }

            when (actionState) {
                BottomBarState.Action.SCAN -> submitTask(SystemCleanerScanTask())
                BottomBarState.Action.WORKING_CANCELABLE -> taskManager.cancel(SDMTool.Type.SYSTEMCLEANER)
                BottomBarState.Action.WORKING -> {}
                BottomBarState.Action.DELETE -> if (systemCleaner.state.first().data != null) {
                    submitTask(SystemCleanerProcessingTask())
                }

                BottomBarState.Action.ONECLICK -> submitTask(SystemCleanerOneClickTask())
            }
        }
        launch {
            if (!generalSettings.oneClickAppCleanerEnabled.value()) {
                log(VERBOSE) { "AppCleaner is disabled one-click mode." }
                return@launch
            }

            when (actionState) {
                BottomBarState.Action.SCAN -> submitTask(AppCleanerScanTask())
                BottomBarState.Action.WORKING_CANCELABLE -> taskManager.cancel(SDMTool.Type.APPCLEANER)
                BottomBarState.Action.WORKING -> {}
                BottomBarState.Action.DELETE -> {
                    if (appCleaner.state.first().data != null && upgradeRepo.isPro()) {
                        submitTask(AppCleanerProcessingTask())
                    } else if (appCleaner.state.first().data.hasData && !corpseFinder.state.first().data.hasData && !systemCleaner.state.first().data.hasData) {
                        navTo(UpgradeRoute())
                    }
                }

                BottomBarState.Action.ONECLICK -> {
                    if (upgradeRepo.isPro()) {
                        submitTask(AppCleanerOneClickTask())
                    } else if (appCleaner.state.first().data.hasData && !corpseFinder.state.first().data.hasData && !systemCleaner.state.first().data.hasData) {
                        navTo(UpgradeRoute())
                    }
                }
            }
        }
        launch {
            if (!generalSettings.oneClickDeduplicatorEnabled.value()) {
                log(VERBOSE) { "Deduplicator is disabled one-click mode." }
                return@launch
            }

            when (actionState) {
                BottomBarState.Action.SCAN -> submitTask(DeduplicatorScanTask())
                BottomBarState.Action.WORKING_CANCELABLE -> taskManager.cancel(SDMTool.Type.DEDUPLICATOR)
                BottomBarState.Action.WORKING -> {}
                BottomBarState.Action.DELETE -> if (deduplicator.state.first().data != null) {
                    submitTask(DeduplicatorDeleteTask())
                }

                BottomBarState.Action.ONECLICK -> submitTask(DeduplicatorOneClickTask())
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

    private suspend fun submitTask(task: SDMTool.Task) {
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
    }

    companion object {
        private val TAG = logTag("Dashboard", "ViewModel")
    }
}
