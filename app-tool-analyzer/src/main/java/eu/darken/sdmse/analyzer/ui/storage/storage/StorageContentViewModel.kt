package eu.darken.sdmse.analyzer.ui.storage.storage

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
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
import eu.darken.sdmse.analyzer.ui.storage.storage.categories.AppCategoryVH
import eu.darken.sdmse.analyzer.ui.storage.storage.categories.MediaCategoryVH
import eu.darken.sdmse.analyzer.ui.storage.storage.categories.SystemCategoryVH
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.navigation.routes.DashboardRoute
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.storage.StorageId
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import eu.darken.sdmse.setup.SetupRoute
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import javax.inject.Inject

@HiltViewModel
class StorageContentViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    analyzer: Analyzer,
    private val taskSubmitter: TaskSubmitter,
) : ViewModel3(dispatcherProvider) {

    private val targetStorageId: StorageId = StorageContentRoute.from(handle).storageId

    init {
        analyzer.data
            .filter { it.categories[targetStorageId].isNullOrEmpty() }
            .take(1)
            .onEach { taskSubmitter.submit(StorageScanTask(targetStorageId)) }
            .catch {
                log(TAG, WARN) { "Storage unavailable $targetStorageId: ${it.asLog()}" }
                navigateTo(DashboardRoute)
            }
            .launchInViewModel()
    }

    val state = combine(
        analyzer.data.filter { data -> data.storages.any { it.id == targetStorageId } },
        analyzer.progress,
    ) { data, progress ->
        val storage = data.storages.single { it.id == targetStorageId }

        val content = data.categories[targetStorageId]
            ?.sortedBy {
                when (it) {
                    is AppCategory -> 1
                    is MediaCategory -> 2
                    is SystemCategory -> 3
                }
            }
            ?.map { content ->
                when (content) {
                    is AppCategory -> AppCategoryVH.Item(
                        storage = storage,
                        content = content,
                        onItemClicked = {
                            if (content.setupIncomplete) {
                                navigateTo(SetupRoute())
                            } else {
                                navigateTo(AppsRoute(storageId = targetStorageId))
                            }
                        }
                    )

                    is MediaCategory -> MediaCategoryVH.Item(
                        storage = storage,
                        content = content,
                        onItemClicked = {
                            if (content.groups.isEmpty()) return@Item
                            navigateTo(ContentRoute(
                                storageId = targetStorageId,
                                groupId = content.groups.single().id,
                                installId = null,
                            ))
                        }
                    )

                    is SystemCategory -> SystemCategoryVH.Item(
                        storage = storage,
                        content = content,
                        onItemClicked = {
                            if (!content.isBrowsable) return@Item
                            val groupId = content.groups.singleOrNull()?.id ?: return@Item
                            // Navigate immediately to avoid flash on the storage content screen.
                            // Deep scan runs in background; ContentFragment shows progress.
                            navigateTo(ContentRoute(
                                storageId = targetStorageId,
                                groupId = groupId,
                                installId = null,
                            ))
                            if (!content.isDeepScanned && progress == null) {
                                launch { taskSubmitter.submit(SystemDeepScanTask(targetStorageId)) }
                            }
                        }
                    )
                }
            }

        State(
            storage = storage,
            content = content,
            progress = progress,
        )
    }.asLiveData2()

    fun refresh() = launch {
        log(TAG) { "refresh()" }
        taskSubmitter.submit(StorageScanTask(target = targetStorageId))
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
            popNavStack()
        }
    }

    data class State(
        val storage: DeviceStorage,
        val content: List<StorageContentAdapter.Item>?,
        val progress: Progress.Data?,
    )

    companion object {
        private val TAG = logTag("Analyzer", "Storage", "Content", "ViewModel")
    }
}