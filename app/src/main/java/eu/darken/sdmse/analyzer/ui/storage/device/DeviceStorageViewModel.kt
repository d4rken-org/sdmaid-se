package eu.darken.sdmse.analyzer.ui.storage.device

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.MainDirections
import eu.darken.sdmse.analyzer.core.Analyzer
import eu.darken.sdmse.analyzer.core.device.DeviceStorageScanTask
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.stats.core.SpaceHistoryRepo
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class DeviceStorageViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val analyzer: Analyzer,
    private val spaceHistoryRepo: SpaceHistoryRepo,
    private val upgradeRepo: UpgradeRepo,
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
        spaceHistoryRepo.getAllHistory(Instant.now() - Duration.ofDays(7)),
        upgradeRepo.upgradeInfo.map { it.isPro },
    ) { data, progress, snapshots, isPro ->
        val snapshotsByStorage = snapshots.groupBy { it.storageId }

        State(
            storages = data.storages.map { storage ->
                DeviceStorageItemVH.Item(
                    storage = storage,
                    snapshots = snapshotsByStorage[storage.id.externalId.toString()]
                        .orEmpty()
                        .sortedBy { it.recordedAt },
                    isPro = isPro,
                    onItemClicked = {
                        if (storage.setupIncomplete) {
                            DeviceStorageFragmentDirections.goToSetup().navigate()
                        } else {
                            DeviceStorageFragmentDirections.actionDeviceStorageFragmentToStorageFragment(
                                it.storage.id
                            ).navigate()
                        }
                    },
                    onTrendClicked = { item ->
                        if (isPro) {
                            MainDirections.goToSpaceHistoryFragment(storageId = item.storage.id.externalId.toString())
                        } else {
                            MainDirections.goToUpgradeFragment()
                        }.navigate()
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
