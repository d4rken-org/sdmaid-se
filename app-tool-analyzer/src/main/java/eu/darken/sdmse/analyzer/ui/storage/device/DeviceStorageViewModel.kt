package eu.darken.sdmse.analyzer.ui.storage.device

import androidx.lifecycle.SavedStateHandle
import androidx.core.os.bundleOf
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.analyzer.R
import eu.darken.sdmse.analyzer.core.Analyzer
import eu.darken.sdmse.analyzer.core.device.DeviceStorageScanTask
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.navigation.navDirections
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel3
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class DeviceStorageViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val analyzer: Analyzer,
) : ViewModel3(dispatcherProvider) {

    init {
        analyzer.data
            .take(1)
            .filter { it.storages.isEmpty() }
            .onEach { analyzer.submit(DeviceStorageScanTask()) }
            .launchInViewModel()
    }


    val state = combine(
        analyzer.data,
        analyzer.progress,
    ) { data, progress ->

        State(
            storages = data.storages.map { storage ->
                DeviceStorageItemVH.Item(
                    storage = storage,
                    onItemClicked = {
                        if (storage.setupIncomplete) {
                            navDirections(eu.darken.sdmse.common.R.id.goToSetup).navigate()
                        } else {
                            navDirections(
                                R.id.action_deviceStorageFragment_to_storageFragment,
                                bundleOf("storageId" to it.storage.id)
                            ).navigate()
                        }
                    }
                )
            },
            progress = progress,
        )
    }.asLiveData2()

    fun refresh() = launch {
        log(TAG) { "refresh()" }
        analyzer.submit(DeviceStorageScanTask())
    }

    data class State(
        val storages: List<DeviceStorageItemVH.Item>?,
        val progress: Progress.Data?,
    )

    companion object {
        private val TAG = logTag("Analyzer", "Storage", "ViewModel")
    }
}