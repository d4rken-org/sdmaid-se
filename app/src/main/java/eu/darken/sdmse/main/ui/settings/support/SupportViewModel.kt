package eu.darken.sdmse.main.ui.settings.support

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.SDMId
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.debug.recorder.core.RecorderModule
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.uix.ViewModel3
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class SupportViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val sdmId: SDMId,
    private val recorderModule: RecorderModule,
) : ViewModel3(dispatcherProvider) {

    val clipboardEvent = SingleLiveEvent<String>()

    val isRecording = recorderModule.state.map { it.isRecording }.asLiveData2()

    private val debugLogFolderStatsInternal = MutableStateFlow(DebugLogFolderStats())
    val debugLogFolderStats = debugLogFolderStatsInternal
        .replayingShare(vmScope)
        .asLiveData2()

    data class DebugLogFolderStats(
        val fileCount: Int = 0,
        val totalSizeBytes: Long = 0L,
    )

    init {
        launch {
            refreshDebugLogFolderStats()
        }
    }

    fun copyInstallID() = launch {
        clipboardEvent.postValue(sdmId.id)
    }

    fun startDebugLog() = launch {
        log(TAG) { "startDebugLog()" }
        recorderModule.startRecorder()
    }

    fun stopDebugLog() = launch {
        log(TAG) { "stopDebugLog()" }
        recorderModule.stopRecorder()
    }

    fun refreshDebugLogFolderStats() = launch {
        log(TAG) { "refreshDebugLogFolderStats()" }

        val logDirs = recorderModule.getLogDirectories()
        var fileCount = 0
        var totalSize = 0L

        logDirs.forEach { logDir ->
            logDir.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    fileCount++
                    totalSize += file.length()
                }
        }

        debugLogFolderStatsInternal.value = DebugLogFolderStats(
            fileCount = fileCount,
            totalSizeBytes = totalSize,
        )

        log(TAG) { "Debug log folder stats: $fileCount files, $totalSize bytes" }
    }

    fun deleteAllDebugLogs() = launch {
        log(TAG) { "deleteAllDebugLogs()" }

        val logDirs = recorderModule.getLogDirectories()
        logDirs.forEach { logDir ->
            logDir.listFiles()?.forEach { sessionDir ->
                try {
                    sessionDir.deleteRecursively()
                    log(TAG) { "Deleted debug log session: ${sessionDir.name}" }
                } catch (e: Exception) {
                    log(TAG) { "Failed to delete debug log session: ${sessionDir.name} - ${e.message}" }
                }
            }
        }

        refreshDebugLogFolderStats()
    }

    companion object {
        private val TAG = logTag("Support", "ViewModel")
    }
}