package eu.darken.sdmse.main.ui.dashboard

import eu.darken.sdmse.appcleaner.core.AppCleaner
import eu.darken.sdmse.appcleaner.core.hasData
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerProcessingTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerScanTask
import eu.darken.sdmse.appcleaner.ui.AppJunkDetailsRoute
import eu.darken.sdmse.appcontrol.core.AppControl
import eu.darken.sdmse.common.SdmSeLinks
import eu.darken.sdmse.common.flow.intervalFlow
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.navigation.routes.AppControlListRoute
import eu.darken.sdmse.common.navigation.routes.DeviceStorageRoute
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.upgrade.isPro
import eu.darken.sdmse.corpsefinder.core.CorpseFinder
import eu.darken.sdmse.corpsefinder.core.hasData
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderDeleteTask
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderScanTask
import eu.darken.sdmse.corpsefinder.ui.CorpseDetailsRoute
import eu.darken.sdmse.deduplicator.core.Deduplicator
import eu.darken.sdmse.deduplicator.core.hasData
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorDeleteTask
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorScanTask
import eu.darken.sdmse.deduplicator.ui.DeduplicatorDetailsRoute
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.taskmanager.getLatestResult
import eu.darken.sdmse.main.ui.dashboard.cards.AnalyzerDashboardCardItem
import eu.darken.sdmse.main.ui.dashboard.cards.AppControlDashboardCardItem
import eu.darken.sdmse.main.ui.dashboard.cards.MotdDashboardCardItem
import eu.darken.sdmse.main.ui.dashboard.cards.SetupDashboardCardItem
import eu.darken.sdmse.main.ui.dashboard.cards.SqueezerDashboardCardItem
import eu.darken.sdmse.main.ui.dashboard.cards.StatsDashboardCardItem
import eu.darken.sdmse.main.ui.dashboard.cards.TitleDashboardCardItem
import eu.darken.sdmse.main.ui.dashboard.cards.ToolDashboardCardItem
import eu.darken.sdmse.main.ui.dashboard.cards.UpdateDashboardCardItem
import eu.darken.sdmse.setup.SetupRoute
import eu.darken.sdmse.squeezer.core.Squeezer
import eu.darken.sdmse.squeezer.ui.SqueezerSetupRoute
import eu.darken.sdmse.stats.ui.ReportsRoute
import eu.darken.sdmse.systemcleaner.core.SystemCleaner
import eu.darken.sdmse.systemcleaner.core.hasData
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerProcessingTask
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerScanTask
import eu.darken.sdmse.systemcleaner.ui.FilterContentDetailsRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import java.time.Duration
import java.time.Instant
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

private val TAG = logTag("Dashboard", "ViewModel")

internal fun DashboardViewModel.buildUpdateInfo(): Flow<UpdateDashboardCardItem?> = updateService.availableUpdate
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

internal fun DashboardViewModel.buildTitleCardItem(): Flow<TitleDashboardCardItem> = combine(
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

internal fun DashboardViewModel.buildCorpseFinderItem(): Flow<ToolDashboardCardItem> = combine(
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

internal fun DashboardViewModel.buildSystemCleanerItem(): Flow<ToolDashboardCardItem> = combine(
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

internal fun DashboardViewModel.buildAppCleanerItem(): Flow<ToolDashboardCardItem> = combine(
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

internal fun DashboardViewModel.buildDeduplicatorItem(): Flow<ToolDashboardCardItem?> = combine(
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

internal fun DashboardViewModel.buildSqueezerItem(): Flow<SqueezerDashboardCardItem?> =
    (squeezer.state as Flow<Squeezer.State?>)
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

internal fun DashboardViewModel.buildAppControlItem(): Flow<AppControlDashboardCardItem?> =
    (appControl.state as Flow<AppControl.State?>)
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

internal fun DashboardViewModel.buildAnalyzerItem(): Flow<AnalyzerDashboardCardItem?> = combine(
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

internal fun DashboardViewModel.buildSetupCardItem(): Flow<SetupDashboardCardItem?> = setupManager.state
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

internal fun DashboardViewModel.buildMotdItem(): Flow<MotdDashboardCardItem?> = motdRepo.motd
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

internal fun DashboardViewModel.buildStatsItem(): Flow<StatsDashboardCardItem?> = combine(
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
