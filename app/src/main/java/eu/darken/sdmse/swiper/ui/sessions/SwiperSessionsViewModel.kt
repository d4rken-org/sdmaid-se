package eu.darken.sdmse.swiper.ui.sessions

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.swiper.core.Swiper
import eu.darken.sdmse.swiper.core.SwiperSettings
import eu.darken.sdmse.swiper.core.tasks.SwiperScanTask
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class SwiperSessionsViewModel @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val swiper: Swiper,
    private val taskManager: TaskManager,
    private val upgradeRepo: UpgradeRepo,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val selectedPaths = MutableStateFlow<Set<APath>>(emptySet())
    private val scanningSessionId = MutableStateFlow<String?>(null)
    private val cancellingSessionId = MutableStateFlow<String?>(null)
    private val refreshingSessionId = MutableStateFlow<String?>(null)

    val state = eu.darken.sdmse.common.flow.combine(
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
    }.asLiveData2()

    fun setSelectedPaths(paths: Set<APath>) {
        log(TAG, INFO) { "setSelectedPaths: $paths" }
        selectedPaths.value = paths
    }

    fun createSession(paths: Set<APath>) = launch {
        log(TAG, INFO) { "createSession(paths=$paths)" }
        if (paths.isEmpty()) return@launch
        swiper.createSession(paths)
    }

    fun continueSession(sessionId: String) = launch {
        log(TAG, INFO) { "continueSession(sessionId=$sessionId)" }
        // Only refresh if cache is empty for this session
        if (!swiper.hasSessionLookups(sessionId)) {
            log(TAG) { "Cache miss for session $sessionId, refreshing lookups..." }
            refreshingSessionId.value = sessionId
            try {
                swiper.refreshSessionLookups(sessionId)
            } finally {
                refreshingSessionId.value = null
            }
        } else {
            log(TAG) { "Cache hit for session $sessionId, skipping refresh" }
        }
        SwiperSessionsFragmentDirections.actionSwiperSessionsFragmentToSwiperSwipeFragment(sessionId).navigate()
    }

    fun scanSession(sessionId: String) = launch {
        log(TAG, INFO) { "scanSession(sessionId=$sessionId)" }
        scanningSessionId.value = sessionId
        try {
            val result = taskManager.submit(SwiperScanTask(sessionId = sessionId))
            log(TAG, INFO) { "Scan result: $result" }
        } finally {
            scanningSessionId.value = null
            cancellingSessionId.value = null
        }
    }

    fun cancelScan() {
        log(TAG, INFO) { "cancelScan()" }
        cancellingSessionId.value = scanningSessionId.value
        taskManager.cancel(SDMTool.Type.SWIPER)
    }

    fun discardSession(sessionId: String) = launch {
        log(TAG, INFO) { "discardSession(sessionId=$sessionId)" }
        if (scanningSessionId.value == sessionId) {
            taskManager.cancel(SDMTool.Type.SWIPER)
        }
        // Suspends on toolLock until cancelled scan releases it
        swiper.discardSession(sessionId)
    }

    fun renameSession(sessionId: String, label: String?) = launch {
        log(TAG, INFO) { "renameSession(sessionId=$sessionId, label=$label)" }
        swiper.updateSessionLabel(sessionId, label)
    }

    data class State(
        val sessionsWithStats: List<Swiper.SessionWithStats>,
        val selectedPaths: Set<APath>,
        val isScanning: Boolean,
        val progress: Progress.Data?,
        val isPro: Boolean,
        val scanningSessionId: String?,
        val cancellingSessionId: String?,
        val refreshingSessionId: String?,
    ) {
        val canCreateNewSession: Boolean = isPro || sessionsWithStats.size < SwiperSettings.FREE_VERSION_SESSION_LIMIT
        val freeVersionLimit: Int = SwiperSettings.FREE_VERSION_LIMIT
        val freeSessionLimit: Int = SwiperSettings.FREE_VERSION_SESSION_LIMIT

        fun isSessionScanning(sessionId: String): Boolean = scanningSessionId == sessionId
        fun isSessionCancelling(sessionId: String): Boolean = cancellingSessionId == sessionId
        fun isSessionRefreshing(sessionId: String): Boolean = refreshingSessionId == sessionId
    }

    companion object {
        private val TAG = logTag("Swiper", "Sessions", "ViewModel")
    }
}
