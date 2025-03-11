package eu.darken.sdmse.appcontrol.ui.list

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.MainDirections
import eu.darken.sdmse.appcontrol.core.AppControl
import eu.darken.sdmse.appcontrol.core.AppControlScanTask
import eu.darken.sdmse.appcontrol.core.AppControlSettings
import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.appcontrol.core.FilterSettings
import eu.darken.sdmse.appcontrol.core.SortSettings
import eu.darken.sdmse.appcontrol.core.export.AppExportTask
import eu.darken.sdmse.appcontrol.core.forcestop.ForceStopTask
import eu.darken.sdmse.appcontrol.core.toggle.AppControlToggleTask
import eu.darken.sdmse.appcontrol.core.uninstall.UninstallTask
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.formatDuration
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallDetails
import eu.darken.sdmse.common.pkgs.isEnabled
import eu.darken.sdmse.common.pkgs.isInstalled
import eu.darken.sdmse.common.pkgs.isSystemApp
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.toSystemTimezone
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.isPro
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.types.PkgExclusion
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.transformLatest
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID
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
    private val taskManager: TaskManager,
) : ViewModel3(dispatcherProvider) {

    init {
        launch {
            val initState = appControl.state.first()
            if (initState.data != null) return@launch
            val initScan = AppControlScanTask(
                screenTime = settings.listSort.value().mode == SortSettings.Mode.SCREEN_TIME
            )
            taskManager.submit(initScan)
        }
    }

    val events = SingleLiveEvent<AppControlListEvents>()
    private val searchQuery = MutableStateFlow("")

    private var lastDataId: UUID? = null
    private val queryCacheLabel = mutableMapOf<Pkg.Id, String>()
    private val AppInfo.normalizedLabel: String
        get() = queryCacheLabel[this.id]
            ?: this.label.get(context)
                .lowercase().trim()
                .also { queryCacheLabel[this.id] = it }

    private val queryCachePkg = mutableMapOf<Pkg.Id, String>()
    private val AppInfo.normalizedPackageName: String
        get() = queryCachePkg[this.id] ?: this.pkg.packageName.also {
            queryCachePkg[this.id] = it
        }

    private val lablrCacheLabel = mutableMapOf<Pkg.Id, String>()
    private val AppInfo.lablrLabel: String
        get() = lablrCacheLabel[this.id] ?: run {
            this.label.get(context)
                .trim()
                .take(1)
                .uppercase()
                .takeIf { it.toDoubleOrNull() == null } ?: "?"
        }.also { lablrCacheLabel[this.id] = it }

    private val lablrCachePkg = mutableMapOf<Pkg.Id, String>()
    private val AppInfo.lablrPkg: String
        get() = lablrCachePkg[this.id] ?: run {
            this.pkg.packageName
                .take(3)
                .uppercase()
                .removeSuffix(".")
                .takeIf { it.toDoubleOrNull() == null } ?: "?"
        }.also { lablrCachePkg[this.id] = it }

    private val lablrCacheUpdated = mutableMapOf<Pkg.Id, String>()
    private val AppInfo.lablrUpdated: String
        get() = lablrCacheUpdated[this.id] ?: run {
            this.pkg.let { it as? InstallDetails }
                ?.updatedAt
                ?.let {
                    val formatter = DateTimeFormatter.ofPattern("MM.uuuu")
                    formatter.format(it.toSystemTimezone())
                }
                ?: "?"
        }.also { lablrCacheUpdated[this.id] = it }

    private val lablrCacheInstalled = mutableMapOf<Pkg.Id, String>()
    private val AppInfo.lablrInstalled: String
        get() = lablrCacheInstalled[this.id] ?: run {
            this.pkg.let { it as? InstallDetails }
                ?.installedAt
                ?.let {
                    val formatter = DateTimeFormatter.ofPattern("MM.uuuu")
                    formatter.format(it.toSystemTimezone())
                }
                ?: "?"
        }.also { lablrCacheInstalled[this.id] = it }

    private val lablrCacheSize = mutableMapOf<Pkg.Id, String>()

    private val AppInfo.lablrSize: String
        get() = lablrCacheSize[this.id] ?: run {
            this.sizes?.total
                ?.let {
                    val hundredMB = 104857600
                    if (it % hundredMB == 0L) it else ((it / hundredMB) + 1L) * hundredMB
                }
                ?.let { Formatter.formatShortFileSize(context, it) } ?: "?"
        }.also { lablrCacheSize[this.id] = it }

    private val lablrCacheScreenTime = mutableMapOf<Pkg.Id, String>()
    private val AppInfo.lablrScreenTime: String
        get() = lablrCacheScreenTime[this.id] ?: run {
            usage?.screenTime?.formatDuration() ?: context.getString(eu.darken.sdmse.common.R.string.general_na_label)
        }.also { lablrCacheScreenTime[this.id] = it }

    data class DisplayOptions(
        val searchQuery: String,
        val listSort: SortSettings,
        val listFilter: FilterSettings
    )

    private val currentDisplayOptions = combine(
        searchQuery,
        settings.listSort.flow,
        settings.listFilter.flow,
    ) { query, listSort, listFilter ->
        DisplayOptions(searchQuery = query, listSort = listSort, listFilter = listFilter)
    }

    val state = currentDisplayOptions.flatMapLatest { displayOptions ->
        appControl.state.transformLatest { state ->
            val initialState = State(
                appInfos = null,
                progressWorker = state.progress,
                progressUI = Progress.Data(),
                options = if (displayOptions.listSort.mode == SortSettings.Mode.SIZE && !state.isSizeInfoAvailable) {
                    displayOptions.copy(listSort = SortSettings())
                } else {
                    displayOptions
                },
                allowAppToggleActions = state.isAppToggleAvailable,
                allowAppForceStopActions = state.isForceStopAvailable,
                hasActiveInfo = state.isActiveInfoAvailable,
                hasSizeInfo = state.isSizeInfoAvailable,
            )
            emit(initialState)

            if (state.data?.id != lastDataId) {
                lablrCacheLabel.clear()
                lablrCacheUpdated.clear()
                lablrCacheInstalled.clear()
                lablrCachePkg.clear()
                lablrCacheSize.clear()
                lablrCacheScreenTime.clear()
            }
            lastDataId = state.data?.id

            val queryNormalized = displayOptions.searchQuery.lowercase()
            val listFilter = displayOptions.listFilter
            val listSort = displayOptions.listSort
            val appInfos = state.data?.apps
                ?.filter { appInfo ->
                    if (queryNormalized.isEmpty()) return@filter true

                    if (appInfo.normalizedPackageName.contains(queryNormalized)) return@filter true

                    if (appInfo.normalizedLabel.contains(queryNormalized)) return@filter true

                    return@filter false
                }
                ?.filter {
                    if (listFilter.tags.contains(FilterSettings.Tag.USER) && it.pkg.isSystemApp) return@filter false
                    if (listFilter.tags.contains(FilterSettings.Tag.SYSTEM) && !it.pkg.isSystemApp) return@filter false
                    if (listFilter.tags.contains(FilterSettings.Tag.ENABLED) && !it.pkg.isEnabled) return@filter false
                    if (listFilter.tags.contains(FilterSettings.Tag.DISABLED) && it.pkg.isEnabled) return@filter false
                    if (listFilter.tags.contains(FilterSettings.Tag.ACTIVE) && it.isActive == false) return@filter false
                    if (listFilter.tags.contains(FilterSettings.Tag.NOT_INSTALLED) && it.pkg.isInstalled) return@filter false

                    return@filter true
                }
                ?.sortedWith(
                    when (listSort.mode) {
                        SortSettings.Mode.NAME -> compareBy {
                            it.normalizedLabel
                        }

                        SortSettings.Mode.PACKAGENAME -> compareBy {
                            it.normalizedPackageName
                        }

                        SortSettings.Mode.LAST_UPDATE -> compareBy {
                            it.updatedAt ?: Instant.EPOCH
                        }

                        SortSettings.Mode.INSTALLED_AT -> compareBy {
                            it.installedAt ?: Instant.EPOCH
                        }

                        SortSettings.Mode.SIZE -> compareBy {
                            it.sizes?.total ?: 0L
                        }

                        SortSettings.Mode.SCREEN_TIME -> compareBy {
                            it.usage?.screenTime ?: Duration.ZERO.minusMillis(1)
                        }
                    }
                )
                ?.let { if (listSort.reversed) it.reversed() else it }
                ?.map { content ->
                    AppControlListRowVH.Item(
                        appInfo = content,
                        sortMode = listSort.mode,
                        lablrName = if (listSort.mode == SortSettings.Mode.NAME) content.lablrLabel else null,
                        lablrPkg = if (listSort.mode == SortSettings.Mode.PACKAGENAME) content.lablrPkg else null,
                        lablrInstalled = if (listSort.mode == SortSettings.Mode.INSTALLED_AT) content.lablrInstalled else null,
                        lablrUpdated = if (listSort.mode == SortSettings.Mode.LAST_UPDATE) content.lablrUpdated else null,
                        lablrSize = if (listSort.mode == SortSettings.Mode.SIZE) content.lablrSize else null,
                        lablrScreenTime = if (listSort.mode == SortSettings.Mode.SCREEN_TIME) content.lablrScreenTime else null,
                        onItemClicked = {
                            AppControlListFragmentDirections.actionAppControlListFragmentToAppActionDialog(
                                content.pkg.id
                            ).navigate()
                        },
                    )
                }
                ?.toList()

            delay(250)

            val finalState = initialState.copy(
                appInfos = appInfos,
                progressUI = null,
            )
            emit(finalState)
        }
    }.asLiveData2()

    fun updateSearchQuery(query: String) {
        log(TAG) { "updateSearchQuery($query)" }
        searchQuery.value = query
    }

    fun ackSizeSortCaveat() = launch {
        log(TAG) { "ackSizeSortCaveat()" }
        settings.ackSizeSortCaveat.value(true)
    }

    fun updateSortMode(mode: SortSettings.Mode) = launch {
        log(TAG) { "updateSortMode($mode)" }

        when (mode) {
            SortSettings.Mode.SIZE -> {
                if (!settings.ackSizeSortCaveat.value()) {
                    events.postValue(AppControlListEvents.ShowSizeSortCaveat)
                }
            }

            SortSettings.Mode.SCREEN_TIME -> {
                val cur = appControl.state.first()
                if (!cur.hasScreenTime) appControl.submit(AppControlScanTask(screenTime = true))
            }

            else -> {}
        }

        settings.listSort.update { it.copy(mode = mode) }
    }

    fun toggleSortDirection() = launch {
        log(TAG) { "toggleSortDirection()" }
        settings.listSort.update {
            it.copy(reversed = !it.reversed)
        }
    }

    fun toggleTag(tag: FilterSettings.Tag) = launch {
        log(TAG) { "toggleTag($tag)" }
        settings.listFilter.update { old ->
            val existing = old.tags.contains(tag)
            val newTags = when (tag) {
                FilterSettings.Tag.USER -> if (existing) {
                    old.tags.minus(tag)
                } else {
                    old.tags.plus(tag).minus(FilterSettings.Tag.SYSTEM)
                }

                FilterSettings.Tag.SYSTEM -> if (existing) {
                    old.tags.minus(tag)
                } else {
                    old.tags.plus(tag).minus(FilterSettings.Tag.USER)
                }

                FilterSettings.Tag.ENABLED -> if (existing) {
                    old.tags.minus(tag)
                } else {
                    old.tags.plus(tag).minus(FilterSettings.Tag.DISABLED)
                }

                FilterSettings.Tag.DISABLED -> if (existing) {
                    old.tags.minus(tag)
                } else {
                    old.tags.plus(tag).minus(FilterSettings.Tag.ENABLED)
                }

                FilterSettings.Tag.ACTIVE -> if (existing) {
                    old.tags.minus(tag)
                } else {
                    old.tags.plus(tag)
                }

                FilterSettings.Tag.NOT_INSTALLED -> if (existing) {
                    old.tags.minus(tag)
                } else {
                    old.tags.plus(tag)
                }
            }
            old.copy(tags = newTags)
        }
    }

    fun clearTags() = launch {
        log(TAG) { "clearTags()" }
        settings.listFilter.update { old -> old.copy(tags = emptySet()) }
    }

    fun refresh(refreshPkgCache: Boolean = false) = launch {
        log(TAG) { "refresh()" }
        lablrCachePkg
        taskManager.submit(AppControlScanTask(refreshPkgCache = refreshPkgCache))
    }

    fun exclude(items: Collection<AppControlListAdapter.Item>) = launch {
        log(TAG) { "exclude(${items.size})" }
        val exclusions = items.map {
            val installId = it.appInfo.installId
            PkgExclusion(pkgId = installId.pkgId)
        }.toSet()
        val createdExclusions = exclusionManager.save(exclusions)
        events.postValue(AppControlListEvents.ExclusionsCreated(createdExclusions.size))
    }

    fun toggle(items: Collection<AppControlListAdapter.Item>, confirmed: Boolean = false) = launch {
        log(TAG) { "toggle(${items.size}, confirmed=$confirmed)" }
        if (!confirmed) {
            events.postValue(AppControlListEvents.ConfirmToggle(items.toList()))
            return@launch
        }
        val targets = items.map { it.appInfo.installId }.toSet()
        taskManager.submit(AppControlToggleTask(targets = targets))
    }

    fun uninstall(items: Collection<AppControlListAdapter.Item>, confirmed: Boolean = false) = launch {
        log(TAG) { "uninstall(${items.size}, confirmed=$confirmed)" }
        if (!confirmed) {
            events.postValue(AppControlListEvents.ConfirmDeletion(items.toList()))
            return@launch
        }
        val targets = items.map { it.appInfo.installId }.toSet()
        val result = taskManager.submit(UninstallTask(targets = targets)) as UninstallTask.Result
        events.postValue(AppControlListEvents.ShowResult(result))
    }

    fun export(items: Collection<AppControlListAdapter.Item>, saveDir: Uri? = null) = launch {
        log(TAG) { "export(${items.size}, saveDir=$saveDir)" }
        if (items.size > 1 && !upgradeRepo.isPro()) {
            MainDirections.goToUpgradeFragment().navigate()
            return@launch
        }

        if (saveDir == null) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            events.postValue(AppControlListEvents.ExportSelectPath(items.toList(), intent))
            return@launch
        }
        val targets = items.map { it.appInfo.installId }.toSet()
        val result = taskManager.submit(AppExportTask(targets = targets, saveDir)) as AppExportTask.Result

        events.postValue(AppControlListEvents.ShowResult(result))
    }

    fun forceStop(items: Collection<AppControlListAdapter.Item>, confirmed: Boolean = false) = launch {
        log(TAG) { "forceStop(${items.size}, confirmed=$confirmed)" }
        if (items.size > 1 && !upgradeRepo.isPro()) {
            MainDirections.goToUpgradeFragment().navigate()
            return@launch
        }

        if (!confirmed) {
            events.postValue(AppControlListEvents.ConfirmForceStop(items.toList()))
            return@launch
        }
        val targets = items.map { it.appInfo.installId }.toSet()
        val result = taskManager.submit(ForceStopTask(targets = targets)) as ForceStopTask.Result
        events.postValue(AppControlListEvents.ShowResult(result))
    }

    data class State(
        val appInfos: List<AppControlListRowVH.Item>?,
        val progressWorker: Progress.Data?,
        val progressUI: Progress.Data?,
        val options: DisplayOptions,
        val allowAppToggleActions: Boolean,
        val allowAppForceStopActions: Boolean,
        val hasSizeInfo: Boolean = false,
        val hasActiveInfo: Boolean = false,
    ) {
        val progress: Progress.Data?
            get() = progressWorker ?: progressUI
    }

    companion object {
        private val TAG = logTag("AppControl", "List", "ViewModel")
    }
}