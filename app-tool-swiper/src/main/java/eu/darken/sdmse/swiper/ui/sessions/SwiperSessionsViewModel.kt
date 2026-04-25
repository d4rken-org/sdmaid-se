package eu.darken.sdmse.swiper.ui.sessions

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.flow.combine
import eu.darken.sdmse.common.navigation.NavigationController
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.picker.PickerRequest
import eu.darken.sdmse.common.picker.PickerResultKey
import eu.darken.sdmse.common.picker.PickerRoute
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import eu.darken.sdmse.swiper.core.FileTypeFilter
import eu.darken.sdmse.swiper.core.SortOrder
import eu.darken.sdmse.swiper.core.Swiper
import eu.darken.sdmse.swiper.core.SwiperSettings
import eu.darken.sdmse.swiper.core.tasks.SwiperScanTask
import eu.darken.sdmse.swiper.ui.SwiperSwipeRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class SwiperSessionsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val swiper: Swiper,
    private val taskSubmitter: TaskSubmitter,
    private val upgradeRepo: UpgradeRepo,
    private val navCtrl: NavigationController,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    private val selectedPaths = MutableStateFlow<Set<APath>>(emptySet())
    private val scanningSessionId = MutableStateFlow<String?>(null)
    private val cancellingSessionId = MutableStateFlow<String?>(null)
    private val refreshingSessionId = MutableStateFlow<String?>(null)

    init {
        navCtrl.consumeResults(PickerResultKey(PICKER_REQUEST_KEY))
            .onEach { result ->
                log(TAG, INFO) { "Picker returned ${result.selectedPaths.size} paths" }
                val paths = result.selectedPaths
                selectedPaths.value = paths
                if (paths.isNotEmpty()) swiper.createSession(paths)
            }
            .launchIn(vmScope)
    }

    val state: StateFlow<State> = combine(
        swiper.getSessionsWithStats(),
        swiper.progress,
        selectedPaths,
        upgradeRepo.upgradeInfo.map { it?.isPro ?: false },
        scanningSessionId,
        cancellingSessionId,
        refreshingSessionId,
    ) { sessionsWithStats, progress, paths, isPro, scanningId, cancellingId, refreshingId ->
        State(
            sessionsWithStats = sessionsWithStats,
            selectedPaths = paths,
            isScanning = progress != null,
            progress = progress,
            isPro = isPro,
            scanningSessionId = scanningId,
            cancellingSessionId = cancellingId,
            refreshingSessionId = refreshingId,
        )
    }.safeStateIn(
        initialValue = State(),
        onError = { State() },
    )

    fun openPicker() {
        log(TAG, INFO) { "openPicker()" }
        navTo(
            PickerRoute(
                request = PickerRequest(
                    requestKey = PICKER_REQUEST_KEY,
                    mode = PickerRequest.PickMode.DIRS,
                    allowedAreas = setOf(
                        DataArea.Type.PORTABLE,
                        DataArea.Type.SDCARD,
                        DataArea.Type.PUBLIC_DATA,
                        DataArea.Type.PUBLIC_MEDIA,
                    ),
                ),
            ),
        )
    }

    fun onUpgradeClick() {
        log(TAG, INFO) { "onUpgradeClick()" }
        navTo(UpgradeRoute())
    }

    fun updateSessionFilter(sessionId: String, filter: FileTypeFilter) = launch {
        log(TAG, INFO) { "updateSessionFilter($sessionId, $filter)" }
        swiper.updateSessionFilter(sessionId, filter)
    }

    fun updateSessionSortOrder(sessionId: String, sortOrder: SortOrder) = launch {
        log(TAG, INFO) { "updateSessionSortOrder($sessionId, $sortOrder)" }
        swiper.updateSessionSortOrder(sessionId, sortOrder)
    }

    fun continueSession(sessionId: String) = launch {
        log(TAG, INFO) { "continueSession($sessionId)" }
        if (!swiper.hasSessionLookups(sessionId)) {
            log(TAG) { "Cache miss for $sessionId, refreshing lookups" }
            refreshingSessionId.value = sessionId
            try {
                swiper.refreshSessionLookups(sessionId)
            } finally {
                refreshingSessionId.value = null
            }
        }
        navTo(SwiperSwipeRoute(sessionId = sessionId))
    }

    fun scanSession(sessionId: String) = launch {
        log(TAG, INFO) { "scanSession($sessionId)" }
        scanningSessionId.value = sessionId
        try {
            taskSubmitter.submit(SwiperScanTask(sessionId = sessionId))
        } finally {
            scanningSessionId.value = null
            cancellingSessionId.value = null
        }
    }

    fun cancelScan() {
        log(TAG, INFO) { "cancelScan()" }
        cancellingSessionId.value = scanningSessionId.value
        taskSubmitter.cancel(SDMTool.Type.SWIPER)
    }

    fun discardSession(sessionId: String) = launch {
        log(TAG, INFO) { "discardSession($sessionId)" }
        if (scanningSessionId.value == sessionId) {
            taskSubmitter.cancel(SDMTool.Type.SWIPER)
        }
        swiper.discardSession(sessionId)
    }

    fun renameSession(sessionId: String, label: String?) = launch {
        log(TAG, INFO) { "renameSession($sessionId, $label)" }
        swiper.updateSessionLabel(sessionId, label)
    }

    data class State(
        val sessionsWithStats: List<Swiper.SessionWithStats> = emptyList(),
        val selectedPaths: Set<APath> = emptySet(),
        val isScanning: Boolean = false,
        val progress: Progress.Data? = null,
        val isPro: Boolean = false,
        val scanningSessionId: String? = null,
        val cancellingSessionId: String? = null,
        val refreshingSessionId: String? = null,
    ) {
        val canCreateNewSession: Boolean = isPro || sessionsWithStats.size < SwiperSettings.FREE_VERSION_SESSION_LIMIT
        val freeVersionLimit: Int = SwiperSettings.FREE_VERSION_LIMIT
        val freeSessionLimit: Int = SwiperSettings.FREE_VERSION_SESSION_LIMIT

        fun isSessionScanning(sessionId: String): Boolean = scanningSessionId == sessionId
        fun isSessionCancelling(sessionId: String): Boolean = cancellingSessionId == sessionId
        fun isSessionRefreshing(sessionId: String): Boolean = refreshingSessionId == sessionId
    }

    companion object {
        internal const val PICKER_REQUEST_KEY = "swiper_sessions_picker"
        private val TAG = logTag("Swiper", "Sessions", "ViewModel")
    }
}
