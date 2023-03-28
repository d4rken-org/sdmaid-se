package eu.darken.sdmse.appcontrol.ui.list

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.appcontrol.core.AppControl
import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.appcontrol.core.tasks.AppControlScanTask
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.features.ExtendedInstallData
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
    private val sortMode = MutableStateFlow(State.SortMode.NAME)
    private val sortReversed = MutableStateFlow(false)

    val state = combine(
        appControl.data,
        appControl.progress,
        searchQuery,
        sortMode,
        sortReversed,
    ) { data, progress, query, sortMode, sortReversed ->
        val appInfos = data?.apps
            ?.filter {
                if (query.isEmpty()) return@filter true
                it.pkg.packageName.contains(query)
            }
            ?.sortedWith(
                when (sortMode) {
                    State.SortMode.NAME -> compareBy<AppInfo> {
                        it.label.get(context).uppercase()
                    }
                    State.SortMode.PACKAGENAME -> compareBy<AppInfo> {
                        it.pkg.packageName.uppercase()
                    }
                    State.SortMode.LAST_UPDATE -> compareBy<AppInfo> {
                        (it.pkg as? ExtendedInstallData)?.updatedAt ?: Instant.EPOCH
                    }
                    State.SortMode.INSTALLED_AT -> compareBy<AppInfo> {
                        (it.pkg as? ExtendedInstallData)?.installedAt ?: Instant.EPOCH
                    }
                }
            )
            ?.let {
                if (sortReversed) it.reversed() else it
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
            sortReversed = sortReversed,
            sortMode = sortMode,
            searchQuery = query,
        )
    }.asLiveData2()

    fun updateSearchQuery(query: String) {
        log(TAG) { "updateSearchQuery($query)" }
        searchQuery.value = query
    }

    fun updateSortMode(mode: State.SortMode) {
        log(TAG) { "updateSortMode($mode)" }
        sortMode.value = mode
    }

    fun toggleSortDirection() {
        log(TAG) { "toggleSortDirection()" }
        sortReversed.value = !sortReversed.value
    }

    data class State(
        val appInfos: List<AppControlListRowVH.Item>?,
        val progress: Progress.Data?,
        val sortReversed: Boolean,
        val sortMode: SortMode,
        val searchQuery: String,
    ) {
        enum class SortMode {
            NAME,
            LAST_UPDATE,
            INSTALLED_AT,
            PACKAGENAME
        }
    }

    companion object {
        private val TAG = logTag("AppControl", "List", "VM")
    }
}