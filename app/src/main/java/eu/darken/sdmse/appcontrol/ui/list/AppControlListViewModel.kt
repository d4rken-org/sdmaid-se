package eu.darken.sdmse.appcontrol.ui.list

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.appcontrol.core.*
import eu.darken.sdmse.appcontrol.core.toggle.AppControlToggleTask
import eu.darken.sdmse.appcontrol.core.uninstall.UninstallTask
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.ExtendedInstallData
import eu.darken.sdmse.common.pkgs.isEnabled
import eu.darken.sdmse.common.pkgs.isSystemApp
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.shizuku.ShizukuManager
import eu.darken.sdmse.common.toSystemTimezone
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.types.PkgExclusion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.time.format.DateTimeFormatter
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
    private val rootManager: RootManager,
    private val shizukuManager: ShizukuManager,
) : ViewModel3(dispatcherProvider) {

    init {
        appControl.state
            .take(1)
            .filter { it.data == null }
            .onEach { appControl.submit(AppControlScanTask()) }
            .launchInViewModel()
    }

    val events = SingleLiveEvent<AppControlListEvents>()
    private val searchQuery = MutableStateFlow("")

    private val queryCacheLabel = mutableMapOf<Pkg.Id, String>()
    private val AppInfo.normalizedLabel: String
        get() = queryCacheLabel[this.id] ?: this.label.get(context).lowercase().also {
            queryCacheLabel[this.id] = it
        }

    private val queryCachePkg = mutableMapOf<Pkg.Id, String>()
    private val AppInfo.normalizedPackageName: String
        get() = queryCachePkg[this.id] ?: this.pkg.packageName.also {
            queryCachePkg[this.id] = it
        }

    private val lablrCacheLabel = mutableMapOf<Pkg.Id, String>()
    private val AppInfo.lablrLabel: String
        get() = lablrCacheLabel[this.id] ?: run {
            this.label.get(context)
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
            this.pkg.let { it as? ExtendedInstallData }
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
            this.pkg.let { it as? ExtendedInstallData }
                ?.installedAt
                ?.let {
                    val formatter = DateTimeFormatter.ofPattern("MM.uuuu")
                    formatter.format(it.toSystemTimezone())
                }
                ?: "?"
        }.also { lablrCacheInstalled[this.id] = it }

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
                progressUI = Progress.DEFAULT_STATE,
                options = displayOptions,
                allowAppToggleActions = state.isAppToggleAvailable,
                hasActiveInfo = state.isActiveInfoAvailable,
            )
            emit(initialState)

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
                            (it.pkg as? ExtendedInstallData)?.updatedAt ?: Instant.EPOCH
                        }

                        SortSettings.Mode.INSTALLED_AT -> compareBy {
                            (it.pkg as? ExtendedInstallData)?.installedAt ?: Instant.EPOCH
                        }
                    }
                )
                ?.let { if (listSort.reversed) it.reversed() else it }
                ?.map { content ->
                    AppControlListRowVH.Item(
                        appInfo = content,
                        lablrName = if (listSort.mode == SortSettings.Mode.NAME) content.lablrLabel else null,
                        lablrPkg = if (listSort.mode == SortSettings.Mode.PACKAGENAME) content.lablrPkg else null,
                        lablrInstalled = if (listSort.mode == SortSettings.Mode.INSTALLED_AT) content.lablrInstalled else null,
                        lablrUpdated = if (listSort.mode == SortSettings.Mode.LAST_UPDATE) content.lablrUpdated else null,
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

    fun updateSortMode(mode: SortSettings.Mode) = launch {
        log(TAG) { "updateSortMode($mode)" }
        settings.listSort.update {
            it.copy(mode = mode)
        }
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
            }
            old.copy(tags = newTags)
        }
    }

    fun clearTags() = launch {
        log(TAG) { "clearTags()" }
        settings.listFilter.update { old -> old.copy(tags = emptySet()) }
    }

    fun refresh() = launch {
        log(TAG) { "refresh()" }
        appControl.submit(AppControlScanTask())
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

    fun toggle(items: Collection<AppControlListAdapter.Item>) = launch {
        log(TAG) { "toggle(${items.size})" }
        val targets = items.map { it.appInfo.installId }.toSet()
        appControl.submit(AppControlToggleTask(targets = targets))
    }

    fun uninstall(items: Collection<AppControlListAdapter.Item>) = launch {
        log(TAG) { "uninstall(${items.size})" }
        val targets = items.map { it.appInfo.installId }.toSet()
        appControl.submit(UninstallTask(targets = targets))
    }

    data class State(
        val appInfos: List<AppControlListRowVH.Item>?,
        val progressWorker: Progress.Data?,
        val progressUI: Progress.Data?,
        val options: DisplayOptions,
        val allowAppToggleActions: Boolean,
        val hasActiveInfo: Boolean = false,
    ) {
        val progress: Progress.Data?
            get() = progressWorker ?: progressUI
    }

    companion object {
        private val TAG = logTag("AppControl", "List", "ViewModel")
    }
}