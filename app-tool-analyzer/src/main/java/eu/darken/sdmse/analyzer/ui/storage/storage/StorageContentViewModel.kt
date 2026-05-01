package eu.darken.sdmse.analyzer.ui.storage.storage

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.analyzer.core.Analyzer
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.analyzer.core.storage.StorageScanTask
import eu.darken.sdmse.analyzer.core.storage.SystemDeepScanTask
import eu.darken.sdmse.analyzer.core.storage.categories.AppCategory
import eu.darken.sdmse.analyzer.core.storage.categories.MediaCategory
import eu.darken.sdmse.analyzer.core.storage.categories.SystemCategory
import eu.darken.sdmse.analyzer.ui.AppsRoute
import eu.darken.sdmse.analyzer.ui.ContentRoute
import eu.darken.sdmse.analyzer.ui.StorageContentRoute
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.navigation.routes.DashboardRoute
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.storage.StorageId
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import eu.darken.sdmse.setup.SetupRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import javax.inject.Inject

@HiltViewModel
class StorageContentViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val analyzer: Analyzer,
    private val taskSubmitter: TaskSubmitter,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    private val routeFlow = MutableStateFlow<StorageContentRoute?>(null)

    fun bindRoute(route: StorageContentRoute) {
        if (routeFlow.value != null) return
        log(TAG, INFO) { "bindRoute(${route.storageId})" }
        routeFlow.value = route
    }

    init {
        // Auto-scan on first arrival when no categories exist for this storage.
        routeFlow
            .filterNotNull()
            .take(1)
            .flatMapLatest { route ->
                analyzer.data
                    .filter { it.categories[route.storageId].isNullOrEmpty() }
                    .take(1)
                    .onEach { taskSubmitter.submit(StorageScanTask(route.storageId)) }
                    .catch {
                        log(TAG, WARN) { "Storage unavailable ${route.storageId}: ${it.asLog()}" }
                        navTo(DashboardRoute)
                    }
            }
            .launchInViewModel()
    }

    val state: StateFlow<State> = routeFlow
        .filterNotNull()
        .flatMapLatest { route ->
            val targetStorageId: StorageId = route.storageId
            combine(
                analyzer.data,
                analyzer.progress,
            ) { data, progress ->
                val storage = data.storages.firstOrNull { it.id == targetStorageId }
                if (storage == null) {
                    State.NotFound
                } else {
                    val rows: List<Row>? = data.categories[targetStorageId]
                        ?.sortedBy {
                            when (it) {
                                is AppCategory -> 1
                                is MediaCategory -> 2
                                is SystemCategory -> 3
                            }
                        }
                        ?.map { content ->
                            when (content) {
                                is AppCategory -> Row.Apps(storage = storage, category = content)
                                is MediaCategory -> Row.Media(storage = storage, category = content)
                                is SystemCategory -> Row.System(storage = storage, category = content)
                            }
                        }
                    State.Ready(
                        storage = storage,
                        rows = rows,
                        progress = progress,
                    )
                }
            }
        }
        .safeStateIn(initialValue = State.Loading, onError = { State.NotFound })

    fun refresh() = launch {
        log(TAG) { "refresh()" }
        val storageId = routeFlow.value?.storageId ?: return@launch
        taskSubmitter.submit(StorageScanTask(target = storageId))
    }

    fun onNavigateBack() = launch {
        log(TAG) { "onNavigateBack()" }
        val activeAnalyzerTasks = taskSubmitter.state.first().tasks
            .filter { it.toolType == SDMTool.Type.ANALYZER }
            .any { it.isActive }
        if (activeAnalyzerTasks) {
            log(TAG) { "Canceling active tasks" }
            taskSubmitter.cancel(SDMTool.Type.ANALYZER)
        } else {
            navUp()
        }
    }

    fun onCategoryClick(row: Row) = launch {
        val storageId = routeFlow.value?.storageId ?: return@launch
        when (row) {
            is Row.Apps -> {
                if (row.category.setupIncomplete) {
                    navTo(SetupRoute())
                } else {
                    navTo(AppsRoute(storageId = storageId))
                }
            }
            is Row.Media -> {
                val groupId = row.category.groups.singleOrNull()?.id
                if (groupId == null) {
                    log(TAG, WARN) { "Media category has 0 or >1 groups, ignoring click" }
                    return@launch
                }
                navTo(ContentRoute(storageId = storageId, groupId = groupId, installId = null))
            }
            is Row.System -> {
                if (!row.category.isBrowsable) return@launch
                val groupId = row.category.groups.singleOrNull()?.id ?: return@launch
                val activeProgress = (state.value as? State.Ready)?.progress
                navTo(ContentRoute(storageId = storageId, groupId = groupId, installId = null))
                if (!row.category.isDeepScanned && activeProgress == null) {
                    launch { taskSubmitter.submit(SystemDeepScanTask(storageId)) }
                }
            }
        }
    }

    sealed interface Row {
        val storage: DeviceStorage
        data class Apps(override val storage: DeviceStorage, val category: AppCategory) : Row
        data class Media(override val storage: DeviceStorage, val category: MediaCategory) : Row
        data class System(override val storage: DeviceStorage, val category: SystemCategory) : Row
    }

    sealed interface State {
        data object Loading : State
        data class Ready(
            val storage: DeviceStorage,
            val rows: List<Row>?,
            val progress: Progress.Data?,
        ) : State
        data object NotFound : State
    }

    companion object {
        private val TAG = logTag("Analyzer", "Storage", "Content", "ViewModel")
    }
}
