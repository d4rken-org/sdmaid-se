package eu.darken.sdmse.appcontrol.ui.list

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.appcontrol.core.*
import eu.darken.sdmse.appcontrol.core.tasks.AppControlScanTask
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.combine
import eu.darken.sdmse.common.pkgs.features.ExtendedInstallData
import eu.darken.sdmse.common.pkgs.isEnabled
import eu.darken.sdmse.common.pkgs.isSystemApp
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import kotlinx.coroutines.flow.*
import java.time.Instant
import javax.inject.Inject

@SuppressLint("StaticFieldLeak")
@HiltViewModel
class AppControlListFragmentVM @Inject constructor(
    private val handle: SavedStateHandle,
    private val dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    private val appControl: AppControl,
    private val taskManager: TaskManager,
    private val settings: AppControlSettings,
) : ViewModel3(dispatcherProvider) {

    init {
        appControl.data
            .take(1)
            .filter { it == null }
            .onEach { appControl.submit(AppControlScanTask()) }
            .launchInViewModel()
    }

    val events = SingleLiveEvent<AppControlListEvents>()
    private val searchQuery = MutableStateFlow("")

    val state = combine(
        appControl.data,
        appControl.progress,
        searchQuery,
        settings.listSort.flow,
        settings.listFilter.flow,
    ) { data, progress, query, listSort, listFilter ->
        val appInfos = data?.apps
            ?.filter {
                if (listFilter.tags.contains(FilterSettings.Tag.USER) && it.pkg.isSystemApp) return@filter false
                if (listFilter.tags.contains(FilterSettings.Tag.SYSTEM) && !it.pkg.isSystemApp) return@filter false
                if (listFilter.tags.contains(FilterSettings.Tag.ENABLED) && !it.pkg.isEnabled) return@filter false
                if (listFilter.tags.contains(FilterSettings.Tag.DISABLED) && it.pkg.isEnabled) return@filter false

                return@filter true
            }
            ?.filter {
                if (query.isEmpty()) return@filter true
                it.pkg.packageName.contains(query)
            }
            ?.sortedWith(
                when (listSort.mode) {
                    SortSettings.Mode.NAME -> compareBy<AppInfo> {
                        it.label.get(context).uppercase()
                    }
                    SortSettings.Mode.PACKAGENAME -> compareBy<AppInfo> {
                        it.pkg.packageName.uppercase()
                    }
                    SortSettings.Mode.LAST_UPDATE -> compareBy<AppInfo> {
                        (it.pkg as? ExtendedInstallData)?.updatedAt ?: Instant.EPOCH
                    }
                    SortSettings.Mode.INSTALLED_AT -> compareBy<AppInfo> {
                        (it.pkg as? ExtendedInstallData)?.installedAt ?: Instant.EPOCH
                    }
                }
            )
            ?.let {
                if (listSort.reversed) it.reversed() else it
            }
            ?.map { content ->
                AppControlListRowVH.Item(
                    appInfo = content,
                    onItemClicked = {
                        AppControlListFragmentDirections.actionAppControlListFragmentToAppActionDialog(
                            content.pkg.id
                        ).navigate()
                    },
                )
            }
            ?.toList()

        State(
            appInfos = appInfos,
            progress = progress,
            searchQuery = query,
            listSort = listSort,
            listFilter = listFilter,
        )
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
            }
            old.copy(tags = newTags)
        }
    }

    data class State(
        val appInfos: List<AppControlListRowVH.Item>?,
        val progress: Progress.Data?,
        val searchQuery: String,
        val listSort: SortSettings,
        val listFilter: FilterSettings,
    )

    companion object {
        private val TAG = logTag("AppControl", "List", "VM")
    }
}