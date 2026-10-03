package eu.darken.sdmse.stats.ui.spacehistory

import android.os.storage.StorageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.stats.R
import eu.darken.sdmse.common.storage.StorageId
import eu.darken.sdmse.common.storage.StorageManager2
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.isProForUi
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.stats.core.SpaceHistoryRepo
import eu.darken.sdmse.stats.core.StatsSettings
import eu.darken.sdmse.stats.core.db.ReportEntity
import eu.darken.sdmse.stats.core.db.SpaceSnapshotEntity
import eu.darken.sdmse.stats.core.forecast.StorageTrendCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
    dispatcherProvider: DispatcherProvider,
    private val spaceHistoryRepo: SpaceHistoryRepo,
    private val upgradeRepo: UpgradeRepo,
    private val storageManager2: StorageManager2,
    statsSettings: StatsSettings,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    private val selectedStorageId = MutableStateFlow<String?>(null)
    private val requestedRange = MutableStateFlow(Range.DAYS_7)
    private var initialStorageConsumed = false

    fun setInitialStorageId(id: String?) {
        if (initialStorageConsumed) return
        initialStorageConsumed = true
        if (id != null) {
            log(TAG, INFO) { "setInitialStorageId($id)" }
            selectedStorageId.value = id
        }
    }

    private val storages = spaceHistoryRepo.getAvailableStorageIds()
        .map { snapshotIds -> resolveStorageOptions(snapshotIds) }

    init {
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
    }

    private val snapshotRetention = statsSettings.retentionSnapshots.flow

    private val effectiveRange = combine(
        requestedRange,
        snapshotRetention,
        upgradeRepo.upgradeInfo.map { it.isPro },
    ) { requested, retention, isPro ->
        Companion.effectiveRange(requested, retention, isPro)
    }.distinctUntilChanged()

    private val snapshots = combine(
        selectedStorageId,
        effectiveRange,
    ) { storageId, range ->
        Query(
            storageId = storageId,
            since = Instant.now() - range.retention,
        )
    }
        .flatMapLatest { query ->
            if (query.storageId == null) {
                flowOf(emptyList())
            } else {
                spaceHistoryRepo.getHistory(query.storageId, query.since)
            }
        }

    private val reportMarkers = effectiveRange
        .map { range -> Instant.now() - range.retention }
        .flatMapLatest { since -> spaceHistoryRepo.getReports(since) }

    val state: StateFlow<State> = eu.darken.sdmse.common.flow.combine(
        upgradeRepo.upgradeInfo.map { it.isPro },
        storages,
        selectedStorageId,
        effectiveRange,
        snapshotRetention.map { visibleRanges(it) },
        snapshots,
        reportMarkers,
    ) { isPro, storages, selectedStorageId, selectedRange, visibleRanges, snapshots, reports ->
        State(
            isPro = isPro,
            showUpgradePrompt = !isPro,
            storages = storages,
            selectedStorageId = selectedStorageId,
            selectedRange = selectedRange,
            visibleRanges = visibleRanges,
            snapshots = snapshots,
            reportMarkers = reports,
            currentUsed = snapshots.lastOrNull()?.let { it.spaceCapacity - it.spaceFree },
            minUsed = snapshots.minOfOrNull { it.spaceCapacity - it.spaceFree },
            maxUsed = snapshots.maxOfOrNull { it.spaceCapacity - it.spaceFree },
            deltaUsed = StorageTrendCalculator.windowTotal(snapshots),
        )
    }.safeStateIn(
        initialValue = State(),
        onError = { State() },
    )

    fun selectRange(range: Range) = launch {
        log(TAG) { "selectRange($range)" }
        if (range != Range.DAYS_7 && !upgradeRepo.isProForUi()) {
            navTo(UpgradeRoute())
            return@launch
        }
        requestedRange.value = range
    }

    fun selectStorage(storageId: String) {
        log(TAG) { "selectStorage($storageId)" }
        selectedStorageId.value = storageId
    }

    fun deleteStorage(storageId: String) = launch {
        log(TAG) { "deleteStorage($storageId)" }
        spaceHistoryRepo.deleteStorage(storageId)
        if (selectedStorageId.value == storageId) {
            selectedStorageId.value = null
        }
    }

    fun openUpgrade() {
        log(TAG) { "openUpgrade()" }
        navTo(UpgradeRoute())
    }

    data class State(
        val isPro: Boolean = false,
        val showUpgradePrompt: Boolean = false,
        val storages: List<StorageOption> = emptyList(),
        val selectedStorageId: String? = null,
        val selectedRange: Range = Range.DAYS_7,
        val visibleRanges: List<Range> = listOf(Range.DAYS_7, Range.DAYS_30, Range.DAYS_90),
        val snapshots: List<SpaceSnapshotEntity> = emptyList(),
        val reportMarkers: List<ReportEntity> = emptyList(),
        val currentUsed: Long? = null,
        val minUsed: Long? = null,
        val maxUsed: Long? = null,
        val deltaUsed: Long? = null,
    )

    data class StorageOption(
        val id: String,
        val label: CaString,
    )

    enum class Range(val retention: Duration) {
        DAYS_7(Duration.ofDays(7)),
        DAYS_30(Duration.ofDays(30)),
        DAYS_90(Duration.ofDays(90)),
        DAYS_180(Duration.ofDays(180)),
        DAYS_365(Duration.ofDays(365)),
    }

    private data class Query(
        val storageId: String?,
        val since: Instant,
    )

    private fun resolveStorageOptions(snapshotIds: List<String>): List<StorageOption> {
        val systemLabels = mutableMapOf<String, CaString>()
        systemLabels[PRIMARY_STORAGE_ID] = R.string.stats_storage_type_primary.toCaString()

        (storageManager2.volumes ?: emptyList())
            .filter { it.isMounted && it.fsUuid != null }
            .forEach { volume ->
                val uuid = StorageId.parseVolumeUuid(volume.fsUuid) ?: return@forEach
                val typeLabel = when {
                    volume.isPrimary == true -> R.string.stats_storage_type_primary.toCaString()
                    volume.disk?.isUsb == true -> R.string.stats_storage_type_portable.toCaString()
                    else -> R.string.stats_storage_type_secondary.toCaString()
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
            R.string.stats_storage_type_primary.toCaString()
        } else {
            R.string.stats_storage_type_secondary.toCaString()
        }
    }

    companion object {
        private val PRIMARY_STORAGE_ID: String =
            (StorageManager.UUID_DEFAULT ?: UUID.fromString("00000000-0000-0000-0000-000000000000")).toString()
        private val TAG = logTag("Stats", "SpaceHistory", "ViewModel")

        internal fun visibleRanges(retention: Duration): List<Range> = Range.entries.filter { range ->
            range == Range.DAYS_7 || range.retention <= retention
        }

        internal fun effectiveRange(requested: Range, retention: Duration, isPro: Boolean): Range {
            if (!isPro) return Range.DAYS_7
            val visible = visibleRanges(retention)
            return if (requested in visible) requested else visible.last()
        }
    }
}
