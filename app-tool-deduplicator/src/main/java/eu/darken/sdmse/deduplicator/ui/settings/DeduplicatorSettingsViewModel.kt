package eu.darken.sdmse.deduplicator.ui.settings

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.flow.combine
import eu.darken.sdmse.common.navigation.NavigationController
import eu.darken.sdmse.common.picker.PickerRequest
import eu.darken.sdmse.common.picker.PickerResultKey
import eu.darken.sdmse.common.picker.PickerRoute
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.deduplicator.core.DeduplicatorSettings
import eu.darken.sdmse.deduplicator.ui.ArbiterConfigRoute
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject


@HiltViewModel
class DeduplicatorSettingsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    upgradeRepo: UpgradeRepo,
    private val settings: DeduplicatorSettings,
    navCtrl: NavigationController,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    private data class TopFlags(
        val scanPaths: List<APath>,
        val allowDeleteAll: Boolean,
        val minSizeBytes: Long,
        val skipUncommon: Boolean,
    )

    private data class SleuthFlags(
        val checksum: Boolean,
        val phash: Boolean,
        val media: Boolean,
    )

    private val topFlagsFlow = kotlinx.coroutines.flow.combine(
        settings.scanPaths.flow,
        settings.allowDeleteAll.flow,
        settings.minSizeBytes.flow,
        settings.skipUncommon.flow,
    ) { scanPaths, allowDeleteAll, minSize, skipUncommon ->
        TopFlags(
            scanPaths = scanPaths.paths.sortedBy { it.path },
            allowDeleteAll = allowDeleteAll,
            minSizeBytes = minSize,
            skipUncommon = skipUncommon,
        )
    }

    private val sleuthFlagsFlow = combine(
        settings.isSleuthChecksumEnabled.flow,
        settings.isSleuthPHashEnabled.flow,
        settings.isSleuthMediaEnabled.flow,
    ) { checksum, phash, media ->
        SleuthFlags(checksum, phash, media)
    }

    val state: StateFlow<State> = combine(
        upgradeRepo.upgradeInfo.map { it.isPro },
        topFlagsFlow,
        sleuthFlagsFlow,
    ) { isPro, top, sleuth ->
        State(
            isPro = isPro,
            scanPaths = top.scanPaths,
            allowDeleteAll = top.allowDeleteAll,
            minSizeBytes = top.minSizeBytes,
            skipUncommon = top.skipUncommon,
            isSleuthChecksumEnabled = sleuth.checksum,
            isSleuthPHashEnabled = sleuth.phash,
            isSleuthMediaEnabled = sleuth.media,
        )
    }.safeStateIn(
        initialValue = State(),
        onError = { State() },
    )

    init {
        navCtrl.consumeResults(PickerResultKey(SCAN_PATHS_REQUEST_KEY))
            .onEach { result ->
                log(TAG) { "Received picker result with ${result.selectedPaths.size} paths" }
                settings.scanPaths.value(DeduplicatorSettings.ScanPaths(paths = result.selectedPaths))
            }
            .launchIn(vmScope)
    }

    fun resetScanPaths() = launch {
        log(TAG) { "resetScanPaths()" }
        settings.scanPaths.value(DeduplicatorSettings.ScanPaths())
    }

    fun onSearchLocationsClick() {
        val current = state.value.scanPaths
        navTo(
            PickerRoute(
                request = PickerRequest(
                    requestKey = SCAN_PATHS_REQUEST_KEY,
                    mode = PickerRequest.PickMode.DIRS,
                    allowedAreas = setOf(
                        DataArea.Type.PORTABLE,
                        DataArea.Type.SDCARD,
                        DataArea.Type.PUBLIC_DATA,
                        DataArea.Type.PUBLIC_MEDIA,
                    ),
                    selectedPaths = current,
                ),
            ),
        )
    }

    fun onArbiterConfigClick() {
        navTo(ArbiterConfigRoute)
    }

    fun setAllowDeleteAll(value: Boolean) = launch { settings.allowDeleteAll.value(value) }
    fun setSkipUncommon(value: Boolean) = launch { settings.skipUncommon.value(value) }
    fun setMinSizeBytes(value: Long) = launch { settings.minSizeBytes.value(value) }
    fun resetMinSizeBytes() = launch { settings.minSizeBytes.value(DeduplicatorSettings.MIN_FILE_SIZE) }
    fun setSleuthChecksumEnabled(value: Boolean) = launch { settings.isSleuthChecksumEnabled.value(value) }
    fun setSleuthPHashEnabled(value: Boolean) = launch { settings.isSleuthPHashEnabled.value(value) }
    fun setSleuthMediaEnabled(value: Boolean) = launch { settings.isSleuthMediaEnabled.value(value) }

    data class State(
        val isPro: Boolean = false,
        val scanPaths: List<APath> = emptyList(),
        val allowDeleteAll: Boolean = false,
        val minSizeBytes: Long = DeduplicatorSettings.MIN_FILE_SIZE,
        val skipUncommon: Boolean = true,
        val isSleuthChecksumEnabled: Boolean = true,
        val isSleuthPHashEnabled: Boolean = false,
        val isSleuthMediaEnabled: Boolean = false,
    )

    companion object {
        private val TAG = logTag("Settings", "Deduplicator", "ViewModel")
        private const val SCAN_PATHS_REQUEST_KEY = "scan.location.paths"
    }
}
