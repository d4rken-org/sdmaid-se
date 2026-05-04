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
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
        // If size sort was selected but the current scan didn't load size info, fall back to
        // default sort so the list isn't empty/wrong.
        val effectiveOptions = if (options.listSort.mode == SortSettings.Mode.SIZE && !acState.canInfoSize) {
            options.copy(listSort = SortSettings())
        } else {
            options
        }

        val allowFilterActive = acState.canInfoActive && settings.moduleActivityEnabled.value()
        val allowSortSize = acState.canInfoSize && settings.moduleSizingEnabled.value()

        val rows = acState.data?.apps?.let { apps -> filterSortRows(apps, effectiveOptions) }

        State(
            rows = rows,
            progress = progress,
            options = effectiveOptions,
            allowActionToggle = acState.canToggle,
            allowActionForceStop = acState.canForceStop,
            allowActionArchive = acState.canArchive,
            allowActionRestore = acState.canRestore,
            allowFilterActive = allowFilterActive,
            allowSortSize = allowSortSize,
            allowSortScreenTime = acState.canInfoScreenTime,
        )
    }.safeStateIn(initialValue = State(), onError = { State() })

    private fun filterSortRows(apps: Collection<AppInfo>, options: DisplayOptions): List<Row> {
        val query = options.searchQuery.lowercase()
        val tags = options.listFilter.tags
        val sort = options.listSort

        return apps
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
            .map { Row(appInfo = it) }
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
        val allowSortSize: Boolean = false,
        val allowSortScreenTime: Boolean = false,
        val allowFilterActive: Boolean = false,
    )

    data class Row(val appInfo: AppInfo) {
        val installId: InstallId get() = appInfo.installId
    }

    sealed interface Event {
        data class ConfirmToggle(val ids: Set<InstallId>) : Event
        data class ConfirmUninstall(val ids: Set<InstallId>) : Event
        data class ConfirmForceStop(val ids: Set<InstallId>) : Event
        data class ConfirmArchive(val ids: Set<InstallId>) : Event
        data class ConfirmRestore(val ids: Set<InstallId>) : Event
        data class ExclusionsCreated(val count: Int) : Event
        data class ExportSelectPath(val ids: Set<InstallId>, val intent: Intent) : Event
        data class ShowResult(val result: AppControlTask.Result) : Event
        data class ShareList(val text: String) : Event
        data object ShowSizeSortCaveat : Event
    }

    companion object {
        private val TAG = logTag("AppControl", "List", "ViewModel")
    }
}
