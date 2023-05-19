package eu.darken.sdmse.analyzer.ui.storage.content

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.analyzer.core.Analyzer
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.analyzer.core.storage.StorageScanTask
import eu.darken.sdmse.analyzer.core.storage.categories.AppCategory
import eu.darken.sdmse.analyzer.ui.storage.apps.AppsItemVH
import eu.darken.sdmse.analyzer.ui.storage.storage.StorageContentFragmentArgs
import eu.darken.sdmse.appcontrol.core.*
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel3
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class ContentFragmentVM @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val analyzer: Analyzer,
) : ViewModel3(dispatcherProvider) {

    private val navArgs by handle.navArgs<StorageContentFragmentArgs>()
    private val targetStorageId = navArgs.storageId

    init {
        analyzer.data
            .take(1)
            .filter { it.contents[targetStorageId].isNullOrEmpty() }
            .onEach { analyzer.submit(StorageScanTask(targetStorageId)) }
            .launchInViewModel()
    }

    val state = combine(
        analyzer.data,
        analyzer.progress,
    ) { data, progress ->
        val storage = data.storages.single { it.id == targetStorageId }
        val contents = data.contents[targetStorageId]!!.filterIsInstance<AppCategory>().single()

        State(
            storage = storage,
            apps = contents.pkgStats.map { app ->
                AppsItemVH.Item(
                    pkgStat = app,
                    onItemClicked = {

                    }
                )
            },
            progress = progress,
        )
    }.asLiveData2()

    data class State(
        val storage: DeviceStorage,
        val apps: List<AppsItemVH.Item>?,
        val progress: Progress.Data?,
    )

    companion object {
        private val TAG = logTag("Analyzer", "Content", "Explorer", "Fragment", "VM")
    }
}