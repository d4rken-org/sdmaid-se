package eu.darken.sdmse.analyzer.ui.storage.device

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.analyzer.core.Analyzer
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.analyzer.core.device.DeviceStorageScanTask
import eu.darken.sdmse.analyzer.ui.StorageContentRoute
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.intervalFlow
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.setup.SetupRoute
import eu.darken.sdmse.stats.core.SpaceHistoryRepo
import eu.darken.sdmse.stats.core.db.SpaceSnapshotEntity
import eu.darken.sdmse.stats.ui.SpaceHistoryRoute
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlin.time.Duration.Companion.hours

@HiltViewModel
class DeviceStorageViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val analyzer: Analyzer,
    spaceHistoryRepo: SpaceHistoryRepo,
    upgradeRepo: UpgradeRepo,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    init {
        analyzer.data
            .take(1)
            .filter { it.storages.isEmpty() }
            .onEach { analyzer.submit(DeviceStorageScanTask()) }
            .launchInViewModel()
    }

    val state: StateFlow<State> = combine(
        analyzer.data,
        analyzer.progress,
        intervalFlow(1.hours).flatMapLatest {
            spaceHistoryRepo.getAllHistory(Instant.now() - Duration.ofDays(7))
        },
        upgradeRepo.upgradeInfo.map { it.isPro },
    ) { data, progress, snapshots, isPro ->
        val snapshotsByStorage = snapshots.groupBy { it.storageId }
        State(
            storages = data.storages.map { storage ->
                Row(
                    storage = storage,
                    snapshots = snapshotsByStorage[storage.id.externalId.toString()]
                        .orEmpty()
                        .sortedBy { it.recordedAt },
                    isPro = isPro,
                )
            },
            progress = progress,
        )
    }.safeStateIn(initialValue = State(), onError = { State() })

    fun refresh() = launch {
        log(TAG) { "refresh()" }
        analyzer.submit(DeviceStorageScanTask())
    }

    fun onStorageClick(row: Row) {
        log(TAG) { "onStorageClick(${row.storage.id})" }
        if (row.storage.setupIncomplete) {
            navTo(SetupRoute())
        } else {
            navTo(StorageContentRoute(storageId = row.storage.id))
        }
    }

    fun onTrendClick(row: Row) {
        log(TAG) { "onTrendClick(${row.storage.id}, isPro=${row.isPro})" }
        if (row.isPro) {
            navTo(SpaceHistoryRoute(storageId = row.storage.id.externalId.toString()))
        } else {
            navTo(UpgradeRoute())
        }
    }

    data class Row(
        val storage: DeviceStorage,
        val snapshots: List<SpaceSnapshotEntity> = emptyList(),
        val isPro: Boolean = false,
    )

    data class State(
        val storages: List<Row> = emptyList(),
        val progress: Progress.Data? = null,
    )

    companion object {
        private val TAG = logTag("Analyzer", "Storage", "ViewModel")
    }
}
