package eu.darken.sdmse.analyzer.ui.storage.storage

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.analyzer.core.Analyzer
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.analyzer.core.storage.StorageScanTask
import eu.darken.sdmse.analyzer.core.storage.categories.AppCategory
import eu.darken.sdmse.analyzer.core.storage.categories.MediaCategory
import eu.darken.sdmse.analyzer.core.storage.categories.SystemCategory
import eu.darken.sdmse.analyzer.ui.storage.storage.categories.AppCategoryVH
import eu.darken.sdmse.analyzer.ui.storage.storage.categories.MediaCategoryVH
import eu.darken.sdmse.analyzer.ui.storage.storage.categories.SystemCategoryVH
import eu.darken.sdmse.appcontrol.core.*
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel3
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class StorageContentFragmentVM @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val analyzer: Analyzer,
) : ViewModel3(dispatcherProvider) {

    private val navArgs by handle.navArgs<StorageContentFragmentArgs>()
    private val targetStorageId = navArgs.storageId

    init {
        analyzer.data
            .take(1)
            .filter { it.categories[targetStorageId].isNullOrEmpty() }
            .onEach { analyzer.submit(StorageScanTask(targetStorageId)) }
            .launchInViewModel()
    }

    val state = combine(
        analyzer.data,
        analyzer.progress,
    ) { data, progress ->
        val storage = data.storages.single { it.id == targetStorageId }
        State(
            storage = storage,
            content = data.categories[targetStorageId]?.map { content ->
                when (content) {
                    is AppCategory -> AppCategoryVH.Item(
                        storage = storage,
                        content = content,
                        onItemClicked = {
                            StorageContentFragmentDirections.actionStorageFragmentToAppsFragment(
                                targetStorageId
                            ).navigate()
                        }
                    )

                    is MediaCategory -> MediaCategoryVH.Item(
                        storage = storage,
                        content = content,
                        onItemClicked = {

                        }
                    )

                    is SystemCategory -> SystemCategoryVH.Item(
                        storage = storage,
                        content = content,
                    )
                }
            },
            progress = progress,
        )
    }.asLiveData2()

    fun refresh() = launch {
        log(TAG) { "refresh()" }
        analyzer.submit(StorageScanTask(target = targetStorageId))
    }

    data class State(
        val storage: DeviceStorage,
        val content: List<StorageContentAdapter.Item>?,
        val progress: Progress.Data?,
    )

    companion object {
        private val TAG = logTag("Analyzer", "Storage", "Content", "Fragment", "VM")
    }
}