package eu.darken.sdmse.stats.ui.spacehistory

import android.os.storage.StorageManager
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.MainDirections
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.storage.StorageId
import eu.darken.sdmse.common.storage.StorageManager2
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.isPro
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.stats.core.SpaceHistoryRepo
import eu.darken.sdmse.stats.core.db.ReportEntity
import eu.darken.sdmse.stats.core.db.SpaceSnapshotEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SpaceHistoryViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val spaceHistoryRepo: SpaceHistoryRepo,
    private val upgradeRepo: UpgradeRepo,
    private val storageManager2: StorageManager2,
) : ViewModel3(dispatcherProvider) {

    private val selectedStorageId = MutableStateFlow(handle.get<String?>("storageId"))
    private val selectedRange = MutableStateFlow(Range.DAYS_7)

    private val storages = spaceHistoryRepo.getAvailableStorageIds()
        .map { snapshotIds -> resolveStorageOptions(snapshotIds) }

    init {
        log(TAG) { "init(storageId=${handle.get<String?>("storageId")})" }

        combine(
            storages,
            selectedStorageId,
        ) { storages, selectedId ->
            storages to selectedId
        }
            .onEach { (storages, selectedId) ->
                if (storages.isEmpty()) return@onEach
                if (selectedId == null || storages.none { it.id == selectedId }) {
                    selectedStorageId.value = storages.first().id
                }
            }
            .launchInViewModel()

        upgradeRepo.upgradeInfo
            .map { it.isPro }
            .onEach { isPro ->
                if (!isPro && selectedRange.value != Range.DAYS_7) {
                    selectedRange.value = Range.DAYS_7
                }
            }
            .launchInViewModel()
    }

    private val snapshots = combine(
        selectedStorageId,
        selectedRange,
        upgradeRepo.upgradeInfo.map { it.isPro },
    ) { storageId, range, isPro ->
        Query(
            storageId = storageId,
            since = Instant.now() - if (isPro) range.retention else Range.DAYS_7.retention,
        )
    }
        .flatMapLatest { query ->
            if (query.storageId == null) {
                flowOf(emptyList())
            } else {
                spaceHistoryRepo.getHistory(query.storageId, query.since)
            }
        }

    private val reportMarkers = combine(
        selectedRange,
        upgradeRepo.upgradeInfo.map { it.isPro },
    ) { range, isPro ->
        Instant.now() - if (isPro) range.retention else Range.DAYS_7.retention
    }
        .flatMapLatest { since -> spaceHistoryRepo.getReports(since) }

    val state = eu.darken.sdmse.common.flow.combine(
        upgradeRepo.upgradeInfo.map { it.isPro },
        storages,
        selectedStorageId,
        selectedRange,
        snapshots,
        reportMarkers,
    ) { isPro, storages, selectedStorageId, selectedRange, snapshots, reports ->
        State(
            isPro = isPro,
            showUpgradePrompt = !isPro,
            storages = storages,
            selectedStorageId = selectedStorageId,
            selectedRange = selectedRange,
            snapshots = snapshots,
            reportMarkers = reports,
            currentUsed = snapshots.lastOrNull()?.let { it.spaceCapacity - it.spaceFree },
            minUsed = snapshots.minOfOrNull { it.spaceCapacity - it.spaceFree },
            maxUsed = snapshots.maxOfOrNull { it.spaceCapacity - it.spaceFree },
            deltaUsed = if (snapshots.size >= 2) {
                val lastUsed = snapshots.last().let { it.spaceCapacity - it.spaceFree }
                val firstUsed = snapshots.first().let { it.spaceCapacity - it.spaceFree }
                lastUsed - firstUsed
            } else null,
        )
    }.asLiveData2()

    fun selectRange(range: Range) = launch {
        log(TAG) { "selectRange($range)" }
        if (range != Range.DAYS_7 && !upgradeRepo.isPro()) {
            MainDirections.goToUpgradeFragment().navigate()
            return@launch
        }
        selectedRange.value = range
    }

    fun selectStorage(storageId: String) {
        log(TAG) { "selectStorage($storageId)" }
        selectedStorageId.value = storageId
    }

    fun openUpgrade() {
        log(TAG) { "openUpgrade()" }
        MainDirections.goToUpgradeFragment().navigate()
    }

    data class State(
        val isPro: Boolean,
        val showUpgradePrompt: Boolean,
        val storages: List<StorageOption>,
        val selectedStorageId: String?,
        val selectedRange: Range,
        val snapshots: List<SpaceSnapshotEntity>,
        val reportMarkers: List<ReportEntity>,
        val currentUsed: Long?,
        val minUsed: Long?,
        val maxUsed: Long?,
        val deltaUsed: Long?,
    )

    data class StorageOption(
        val id: String,
        val label: CaString,
    )

    enum class Range(val retention: Duration) {
        DAYS_7(Duration.ofDays(7)),
        DAYS_30(Duration.ofDays(30)),
        DAYS_90(Duration.ofDays(90)),
    }

    private data class Query(
        val storageId: String?,
        val since: Instant,
    )

    private fun resolveStorageOptions(snapshotIds: List<String>): List<StorageOption> {
        val systemLabels = mutableMapOf<String, CaString>()
        systemLabels[PRIMARY_STORAGE_ID] = R.string.analyzer_storage_type_primary_title.toCaString()

        (storageManager2.volumes ?: emptyList())
            .filter { it.isMounted && it.fsUuid != null }
            .forEach { volume ->
                val uuid = StorageId.parseVolumeUuid(volume.fsUuid) ?: return@forEach
                val typeLabel = when {
                    volume.isPrimary == true -> R.string.analyzer_storage_type_primary_title.toCaString()
                    volume.disk?.isUsb == true -> R.string.analyzer_storage_type_tertiary_title.toCaString()
                    else -> R.string.analyzer_storage_type_secondary_title.toCaString()
                }
                systemLabels[uuid.toString()] = typeLabel
            }

        return snapshotIds.map { storageId ->
            StorageOption(
                id = storageId,
                label = systemLabels[storageId] ?: fallbackStorageLabel(storageId),
            )
        }
    }

    private fun fallbackStorageLabel(storageId: String): CaString {
        return if (storageId == PRIMARY_STORAGE_ID) {
            R.string.analyzer_storage_type_primary_title.toCaString()
        } else {
            R.string.analyzer_storage_type_secondary_title.toCaString()
        }
    }

    companion object {
        private val PRIMARY_STORAGE_ID: String =
            (StorageManager.UUID_DEFAULT ?: UUID.fromString("00000000-0000-0000-0000-000000000000")).toString()
        private val TAG = logTag("Stats", "SpaceHistory", "ViewModel")
    }
}
