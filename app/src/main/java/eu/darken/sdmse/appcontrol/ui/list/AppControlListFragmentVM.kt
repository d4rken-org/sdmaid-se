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
import eu.darken.sdmse.common.pkgs.isSystemApp
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import kotlinx.coroutines.flow.*
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
    private val searchQuery = MutableStateFlow<String>("")

    val items = combine(
        appControl.data,
        appControl.progress,
        searchQuery,
    ) { data, progress, query ->
        val appInfos = data?.apps
            ?.filter {
                if (query.isEmpty()) return@filter true
                it.pkg.packageName.contains(query)
            }
            ?.sortedWith(
                compareBy<AppInfo> { it.pkg.isSystemApp }.thenBy { it.label.get(context) }
            )
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
        )
    }.asLiveData2()

    fun updateSearchQuery(query: String) {
        log(TAG) { "updateSearchQuery(): $query" }
        searchQuery.value = query
    }

    data class State(
        val appInfos: List<AppControlListRowVH.Item>?,
        val progress: Progress.Data?,
        val searchQuery: String,
    )

    companion object {
        private val TAG = logTag("AppControl", "List", "VM")
    }
}