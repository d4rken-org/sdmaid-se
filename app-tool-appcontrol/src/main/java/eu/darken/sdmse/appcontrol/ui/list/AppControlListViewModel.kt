package eu.darken.sdmse.appcontrol.ui.list

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.appcontrol.core.AppControl
import eu.darken.sdmse.appcontrol.core.AppControlScanTask
import eu.darken.sdmse.appcontrol.core.AppControlSettings
import eu.darken.sdmse.appcontrol.core.AppControlTask
import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.appcontrol.core.FilterSettings
import eu.darken.sdmse.appcontrol.core.SortSettings
import eu.darken.sdmse.appcontrol.core.archive.ArchiveTask
import eu.darken.sdmse.appcontrol.core.export.AppExportTask
import eu.darken.sdmse.appcontrol.core.forcestop.ForceStopTask
import eu.darken.sdmse.appcontrol.core.restore.RestoreTask
import eu.darken.sdmse.appcontrol.core.toggle.AppControlToggleTask
import eu.darken.sdmse.appcontrol.core.uninstall.UninstallTask
import eu.darken.sdmse.appcontrol.ui.AppActionRoute
import eu.darken.sdmse.common.compose.snackbar.ToolListEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.SingleEventFlow
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.AppStore
import eu.darken.sdmse.common.pkgs.features.InstallDetails
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.isEnabled
import eu.darken.sdmse.common.pkgs.isInstalled
import eu.darken.sdmse.common.pkgs.isSystemApp
import eu.darken.sdmse.common.pkgs.toKnownPkg
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.isPro
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.types.PkgExclusion
import eu.darken.sdmse.exclusion.ui.ExclusionsListRoute
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import eu.darken.sdmse.setup.IncompleteSetupException
import eu.darken.sdmse.setup.SetupBinding
import eu.darken.sdmse.setup.SetupModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@SuppressLint("StaticFieldLeak")
@HiltViewModel
class AppControlListViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    private val appControl: AppControl,
    private val settings: AppControlSettings,
    private val exclusionManager: ExclusionManager,
    private val upgradeRepo: UpgradeRepo,
    private val taskManager: TaskSubmitter,
    @SetupBinding(SetupModule.Type.USAGE_STATS) private val usageStatsSetupModule: SetupModule,
    @SetupBinding(SetupModule.Type.STORAGE) private val storageSetupModule: SetupModule,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    init {
        // Start an initial scan if AppControl has no data yet. AppControl is the entry point for
        // Dashboard, the launcher shortcut, and ExclusionList's "Add Pkg Exclusion" FAB — so the
        // empty-data state must NOT navUp(); it must trigger a scan and render a placeholder until
        // results arrive.
        launch {
            val initState = appControl.state.first()
            if (initState.data != null) return@launch
            taskManager.submit(buildScanTask())
        }
        // Reset a persisted sort whose required setup has been revoked (e.g. usage access
        // permission removed while sorted by screen time), otherwise the stale mode wedges
        // the list in an all-N/A state with no UI path out.
        appControl.state
            .onEach { acState ->
                val sort = settings.listSort.value()
                if ((requiredSetupFor(sort.mode) intersect acState.missingSetup).isNotEmpty()) {
                    log(TAG, INFO) { "Resetting sort ${sort.mode}, missing setup: ${acState.missingSetup}" }
                    settings.listSort.value(SortSettings())
                }
            }
            .launchInViewModel()
    }

    private fun requiredSetupFor(mode: SortSettings.Mode): Set<SetupModule.Type> = when (mode) {
        SortSettings.Mode.SIZE -> setOf(SetupModule.Type.USAGE_STATS, SetupModule.Type.STORAGE)
        SortSettings.Mode.SCREEN_TIME -> setOf(SetupModule.Type.USAGE_STATS)
        else -> emptySet()
    }

    fun onScreenResume() = launch {
        // Permission state is cached in the setup modules and can go stale if usage access or
        // storage permissions change while this screen isn't in front (system settings, setup
        // screen, adb). Without this, the setup-required gate decides on outdated state: it can
        // show a dialog for an already-granted permission, whose setup screen then instantly
        // auto-closes ("everything complete") — a confusing dead-end loop.
        log(TAG) { "onScreenResume(): refreshing setup modules" }
        usageStatsSetupModule.refresh()
        storageSetupModule.refresh()
    }

    val events = SingleEventFlow<Event>()
    private val searchQuery = MutableStateFlow("")
    private val labelCache = mutableMapOf<Pkg.Id, String>()
    private val packageNameCache = mutableMapOf<Pkg.Id, String>()

    private val displayOptions = combine(
        searchQuery,
        settings.listSort.flow,
        settings.listFilter.flow,
    ) { query, sort, filter ->
        DisplayOptions(searchQuery = query, listSort = sort, listFilter = filter)
    }

    val state: StateFlow<State> = combine(
        appControl.state,
        appControl.progress,
        displayOptions,
    ) { acState, progress, options ->
        // If the selected sort's backing data wasn't loaded by the current scan (missing or
        // revoked permission, stale scan), order rows by the default sort instead of garbage
        // null values. Only row ordering falls back — the displayed options keep the requested
        // sort, so the sort sheet doesn't flap while the post-selection rescan is running.
        // Permanently impossible sorts are reset in the persisted settings (see init).
        val sortDataMissing = when (options.listSort.mode) {
            SortSettings.Mode.SIZE -> acState.data?.hasInfoSize != true
            SortSettings.Mode.SCREEN_TIME -> acState.data?.hasInfoScreenTime != true
            else -> false
        }
        val orderingOptions = if (sortDataMissing) options.copy(listSort = SortSettings()) else options

        val allowFilterActive = acState.canInfoActive && settings.moduleActivityEnabled.value()

        val rows = acState.data?.apps?.let { apps -> filterSortRows(apps, orderingOptions) }

        State(
            rows = rows,
            progress = progress,
            options = options,
            allowActionToggle = acState.canToggle,
            allowActionForceStop = acState.canForceStop,
            allowActionArchive = acState.canArchive,
            allowActionRestore = acState.canRestore,
            allowFilterActive = allowFilterActive,
            sizeSortModuleEnabled = settings.moduleSizingEnabled.value(),
        )
    }
        .combine(settings.listFastScrollerEnabled.flow) { base, fastScrollerEnabled ->
            // Outer combine: toggling the fast scroller setting must not invalidate the row
            // pipeline above (which would flash the loading overlay).
            base.copy(fastScrollerEnabled = fastScrollerEnabled)
        }
        .safeStateIn(initialValue = State(), onError = { State() })

    private fun filterSortRows(apps: Collection<AppInfo>, options: DisplayOptions): List<Row> {
        val query = options.searchQuery.lowercase()
        val tags = options.listFilter.tags
        val sort = options.listSort

        val rows = apps
            .asSequence()
            .filter { app ->
                if (query.isEmpty()) return@filter true
                normalizedPackageName(app).contains(query) || normalizedLabel(app).contains(query)
            }
            .filter { app ->
                if (tags.contains(FilterSettings.Tag.USER) && app.pkg.isSystemApp) return@filter false
                if (tags.contains(FilterSettings.Tag.SYSTEM) && !app.pkg.isSystemApp) return@filter false
                if (tags.contains(FilterSettings.Tag.ENABLED) && !app.pkg.isEnabled) return@filter false
                if (tags.contains(FilterSettings.Tag.DISABLED) && app.pkg.isEnabled) return@filter false
                if (tags.contains(FilterSettings.Tag.ACTIVE) && app.isActive == false) return@filter false
                if (tags.contains(FilterSettings.Tag.NOT_INSTALLED) && app.pkg.isInstalled) return@filter false
                true
            }
            .toList()
            .sortedWith(
                when (sort.mode) {
                    SortSettings.Mode.NAME -> compareBy { normalizedLabel(it) }
                    SortSettings.Mode.PACKAGENAME -> compareBy { normalizedPackageName(it) }
                    SortSettings.Mode.LAST_UPDATE -> compareBy { it.updatedAt ?: Instant.EPOCH }
                    SortSettings.Mode.INSTALLED_AT -> compareBy { it.installedAt ?: Instant.EPOCH }
                    SortSettings.Mode.SIZE -> compareBy { it.sizes?.total ?: 0L }
                    SortSettings.Mode.SCREEN_TIME -> compareBy { it.usage?.screenTime ?: Duration.ZERO.minusMillis(1) }
                },
            )
            .let { if (sort.reversed) it.reversed() else it }
            .map { app ->
                Row(
                    appInfo = app,
                    sectionKeyName = sectionKeyOf(normalizedLabel(app)),
                    sectionKeyPkg = sectionKeyOf(normalizedPackageName(app)),
                )
            }

        log(TAG, INFO) {
            "Filtered ${apps.size} → ${rows.size} apps (tags=$tags, sort=${sort.mode}, query='$query')"
        }

        return rows
    }

    private fun sectionKeyOf(value: String): String {
        val firstChar = value.firstOrNull() ?: return "?"
        return firstChar.uppercaseChar().toString()
    }

    private fun normalizedLabel(app: AppInfo): String = labelCache.getOrPut(app.id) {
        app.label.get(context).lowercase().trim()
    }

    private fun normalizedPackageName(app: AppInfo): String = packageNameCache.getOrPut(app.id) {
        app.pkg.packageName
    }

    fun onTapRow(installId: InstallId) {
        log(TAG, INFO) { "onTapRow($installId)" }
        navTo(AppActionRoute(installId = installId))
    }

    fun onSearchQueryChanged(query: String) {
        log(TAG) { "onSearchQueryChanged($query)" }
        searchQuery.value = query
    }

    fun onAckSizeSortCaveat() = launch {
        log(TAG) { "onAckSizeSortCaveat()" }
        settings.ackSizeSortCaveat.value(true)
    }

    fun onSortModeChanged(mode: SortSettings.Mode) = launch {
        log(TAG) { "onSortModeChanged($mode)" }
        if (mode == SortSettings.Mode.SIZE && !settings.moduleSizingEnabled.value()) {
            log(TAG, WARN) { "Ignoring SIZE sort, sizing module is disabled" }
            return@launch
        }

        val missing = requiredSetupFor(mode) intersect appControl.state.first().missingSetup
        if (missing.isNotEmpty()) {
            log(TAG, INFO) { "Sort mode $mode requires missing setup: $missing" }
            errorEvents.emit(IncompleteSetupException(missing))
            return@launch
        }

        settings.listSort.update { it.copy(mode = mode) }
        when (mode) {
            SortSettings.Mode.SIZE -> {
                if (!settings.ackSizeSortCaveat.value()) {
                    events.emit(Event.ShowSizeSortCaveat)
                }
                if (appControl.state.first().data?.hasInfoSize != true) refresh()
            }

            SortSettings.Mode.SCREEN_TIME -> {
                if (appControl.state.first().data?.hasInfoScreenTime != true) refresh()
            }

            else -> {}
        }
    }

    fun onSortDirectionToggle() = launch {
        log(TAG) { "onSortDirectionToggle()" }
        settings.listSort.update { it.copy(reversed = !it.reversed) }
    }

    fun onTagToggle(tag: FilterSettings.Tag) = launch {
        log(TAG) { "onTagToggle($tag)" }
        settings.listFilter.update { old ->
            val present = old.tags.contains(tag)
            val newTags = when (tag) {
                FilterSettings.Tag.USER -> if (present) {
                    old.tags - tag
                } else {
                    old.tags + tag - FilterSettings.Tag.SYSTEM
                }

                FilterSettings.Tag.SYSTEM -> if (present) {
                    old.tags - tag
                } else {
                    old.tags + tag - FilterSettings.Tag.USER
                }

                FilterSettings.Tag.ENABLED -> if (present) {
                    old.tags - tag
                } else {
                    old.tags + tag - FilterSettings.Tag.DISABLED - FilterSettings.Tag.NOT_INSTALLED
                }

                FilterSettings.Tag.DISABLED -> if (present) {
                    old.tags - tag
                } else {
                    old.tags + tag - FilterSettings.Tag.ENABLED
                }

                FilterSettings.Tag.ACTIVE -> if (present) old.tags - tag else old.tags + tag

                FilterSettings.Tag.NOT_INSTALLED -> if (present) {
                    old.tags - tag
                } else {
                    old.tags + tag - FilterSettings.Tag.ENABLED
                }
            }
            old.copy(tags = newTags)
        }
    }

    fun onTagsReset() = launch {
        log(TAG) { "onTagsReset()" }
        settings.listFilter.update { FilterSettings() }
    }

    fun onToggleFastScroller() = launch {
        log(TAG) { "onToggleFastScroller()" }
        settings.listFastScrollerEnabled.update { current -> !current }
    }

    fun onRefresh(refreshPkgCache: Boolean = false) = launch {
        log(TAG) { "onRefresh($refreshPkgCache)" }
        taskManager.submit(buildScanTask(refreshPkgCache))
    }

    private suspend fun refresh() = taskManager.submit(buildScanTask())

    private suspend fun buildScanTask(refreshPkgCache: Boolean = false) = AppControlScanTask(
        refreshPkgCache = refreshPkgCache,
        loadInfoSize = settings.moduleSizingEnabled.value(),
        loadInfoActive = settings.moduleActivityEnabled.value(),
        loadInfoScreenTime = settings.listSort.value().mode == SortSettings.Mode.SCREEN_TIME,
        includeMultiUser = settings.includeMultiUserEnabled.value(),
    )

    fun onExcludeSelected(ids: Set<InstallId>) = launch {
        log(TAG, INFO) { "onExcludeSelected(${ids.size})" }
        val valid = liveIntersect(ids)
        if (valid.isEmpty()) return@launch
        val exclusions = valid.map { PkgExclusion(pkgId = it.pkgId) }.toSet()
        val created = exclusionManager.save(exclusions)
        events.emit(Event.ExclusionsCreated(created.size))
    }

    fun onToggleRequested(ids: Set<InstallId>) = launch {
        if (ids.isEmpty()) return@launch
        events.emit(Event.ConfirmToggle(ids))
    }

    fun onToggleConfirmed(ids: Set<InstallId>) = launch {
        log(TAG, INFO) { "onToggleConfirmed(${ids.size})" }
        val valid = liveIntersect(ids)
        if (valid.isEmpty()) return@launch
        val result = taskManager.submit(AppControlToggleTask(targets = valid)) as AppControlToggleTask.Result
        events.emit(Event.ShowResult(result))
    }

    fun onUninstallRequested(ids: Set<InstallId>) = launch {
        if (ids.isEmpty()) return@launch
        events.emit(Event.ConfirmUninstall(ids))
    }

    fun onUninstallConfirmed(ids: Set<InstallId>) = launch {
        log(TAG, INFO) { "onUninstallConfirmed(${ids.size})" }
        val valid = liveIntersect(ids)
        if (valid.isEmpty()) return@launch
        val result = taskManager.submit(UninstallTask(targets = valid)) as UninstallTask.Result
        events.emit(Event.ShowResult(result))
    }

    fun onForceStopRequested(ids: Set<InstallId>) = launch {
        if (ids.isEmpty()) return@launch
        if (ids.size > 1 && !upgradeRepo.isPro()) {
            navTo(UpgradeRoute())
            return@launch
        }
        events.emit(Event.ConfirmForceStop(ids))
    }

    fun onForceStopConfirmed(ids: Set<InstallId>) = launch {
        log(TAG, INFO) { "onForceStopConfirmed(${ids.size})" }
        val valid = liveIntersect(ids)
        if (valid.isEmpty()) return@launch
        if (valid.size > 1 && !upgradeRepo.isPro()) {
            navTo(UpgradeRoute())
            return@launch
        }
        val result = taskManager.submit(ForceStopTask(targets = valid)) as ForceStopTask.Result
        events.emit(Event.ShowResult(result))
    }

    fun onArchiveRequested(ids: Set<InstallId>) = launch {
        if (ids.isEmpty()) return@launch
        if (ids.size > 1 && !upgradeRepo.isPro()) {
            navTo(UpgradeRoute())
            return@launch
        }
        events.emit(Event.ConfirmArchive(ids))
    }

    fun onArchiveConfirmed(ids: Set<InstallId>) = launch {
        log(TAG, INFO) { "onArchiveConfirmed(${ids.size})" }
        val valid = liveIntersect(ids)
        if (valid.isEmpty()) return@launch
        if (valid.size > 1 && !upgradeRepo.isPro()) {
            navTo(UpgradeRoute())
            return@launch
        }
        val result = taskManager.submit(ArchiveTask(targets = valid)) as ArchiveTask.Result
        events.emit(Event.ShowResult(result))
    }

    fun onRestoreRequested(ids: Set<InstallId>) = launch {
        if (ids.isEmpty()) return@launch
        if (ids.size > 1 && !upgradeRepo.isPro()) {
            navTo(UpgradeRoute())
            return@launch
        }
        events.emit(Event.ConfirmRestore(ids))
    }

    fun onRestoreConfirmed(ids: Set<InstallId>) = launch {
        log(TAG, INFO) { "onRestoreConfirmed(${ids.size})" }
        val valid = liveIntersect(ids)
        if (valid.isEmpty()) return@launch
        if (valid.size > 1 && !upgradeRepo.isPro()) {
            navTo(UpgradeRoute())
            return@launch
        }
        val result = taskManager.submit(RestoreTask(targets = valid)) as RestoreTask.Result
        events.emit(Event.ShowResult(result))
    }

    fun onExportRequested(ids: Set<InstallId>) = launch {
        log(TAG, INFO) { "onExportRequested(${ids.size})" }
        if (ids.isEmpty()) return@launch
        if (ids.size > 1 && !upgradeRepo.isPro()) {
            navTo(UpgradeRoute())
            return@launch
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        events.emit(Event.ExportSelectPath(ids, intent))
    }

    fun onExportPathPicked(ids: Set<InstallId>, uri: Uri?) = launch {
        log(TAG, INFO) { "onExportPathPicked(${ids.size}, $uri)" }
        if (uri == null) return@launch
        val valid = liveIntersect(ids)
        if (valid.isEmpty()) return@launch
        if (valid.size > 1 && !upgradeRepo.isPro()) {
            navTo(UpgradeRoute())
            return@launch
        }
        val result = taskManager.submit(AppExportTask(targets = valid, savePath = uri)) as AppExportTask.Result
        events.emit(Event.ShowResult(result))
    }

    fun onShareList(ids: Set<InstallId>) = launch {
        log(TAG, INFO) { "onShareList(${ids.size})" }
        val data = appControl.state.first().data ?: return@launch
        val items = ids.mapNotNull { id -> data.apps.firstOrNull { it.installId == id } }
        if (items.isEmpty()) return@launch

        val appsList = items.joinToString("\n") { app ->
            val pkg = app.pkg
            val label = app.label.get(context)
            val pkgName = pkg.packageName
            val versionName = pkg.versionName ?: "?"
            val versionCode = pkg.versionCode

            val store = (pkg as? InstallDetails)?.installerInfo?.installer
                ?.let { installer ->
                    (installer as? AppStore) ?: installer.id.toKnownPkg() as? AppStore
                }

            buildString {
                append("- **$label** `$pkgName` v$versionName ($versionCode)")
                store?.urlGenerator?.invoke(pkg.id)?.let { storeLink ->
                    append(" [${store.storeLabel}]($storeLink)")
                }
            }
        }
        val title = context.getString(eu.darken.sdmse.appcontrol.R.string.appcontrol_share_list_title)
        val footer = context.getString(eu.darken.sdmse.appcontrol.R.string.appcontrol_share_list_footer)
        val text = "# $title\n\n$appsList\n\n---\n*$footer*"
        events.emit(Event.ShareList(text))
    }

    fun onShowExclusionsList() {
        navTo(ExclusionsListRoute)
    }

    private suspend fun liveIntersect(ids: Set<InstallId>): Set<InstallId> {
        val data = appControl.state.first().data ?: return emptySet()
        val live = data.apps.map { it.installId }.toSet()
        return ids intersect live
    }

    data class DisplayOptions(
        val searchQuery: String = "",
        val listSort: SortSettings = SortSettings(),
        val listFilter: FilterSettings = FilterSettings(),
    )

    data class State(
        val rows: List<Row>? = null,
        val progress: Progress.Data? = null,
        val options: DisplayOptions = DisplayOptions(),
        val allowActionToggle: Boolean = false,
        val allowActionForceStop: Boolean = false,
        val allowActionArchive: Boolean = false,
        val allowActionRestore: Boolean = false,
        val sizeSortModuleEnabled: Boolean = false,
        val allowFilterActive: Boolean = false,
        val fastScrollerEnabled: Boolean = false,
    )

    data class Row(
        val appInfo: AppInfo,
        val sectionKeyName: String = "",
        val sectionKeyPkg: String = "",
    ) {
        val installId: InstallId get() = appInfo.installId
    }

    sealed interface Event {
        data class ConfirmToggle(val ids: Set<InstallId>) : Event
        data class ConfirmUninstall(val ids: Set<InstallId>) : Event
        data class ConfirmForceStop(val ids: Set<InstallId>) : Event
        data class ConfirmArchive(val ids: Set<InstallId>) : Event
        data class ConfirmRestore(val ids: Set<InstallId>) : Event
        data class ExclusionsCreated(override val count: Int) : Event, ToolListEvent.ShowExclusionsCreated
        data class ExportSelectPath(val ids: Set<InstallId>, val intent: Intent) : Event
        data class ShowResult(override val result: AppControlTask.Result) : Event, ToolListEvent.ShowTaskResult
        data class ShareList(val text: String) : Event
        data object ShowSizeSortCaveat : Event
    }

    companion object {
        private val TAG = logTag("AppControl", "List", "ViewModel")
    }
}
