package eu.darken.sdmse.compressor.ui.setup

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.MimeTypeTool
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathGateway
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.isFile
import eu.darken.sdmse.common.files.walk
import eu.darken.sdmse.common.flow.combine
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.storage.StorageEnvironment
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.compressor.core.CompressibleImage
import eu.darken.sdmse.compressor.core.CompressionEstimator
import eu.darken.sdmse.compressor.core.Compressor
import eu.darken.sdmse.compressor.core.CompressorSettings
import eu.darken.sdmse.compressor.core.hasData
import eu.darken.sdmse.compressor.core.tasks.CompressorScanTask
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import java.time.Duration
import javax.inject.Inject

@HiltViewModel
class CompressorSetupViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val settings: CompressorSettings,
    private val compressor: Compressor,
    private val taskManager: TaskManager,
    private val compressionEstimator: CompressionEstimator,
    private val gatewaySwitch: GatewaySwitch,
    private val storageEnvironment: StorageEnvironment,
    private val mimeTypeTool: MimeTypeTool,
) : ViewModel3(dispatcherProvider) {

    val events = SingleLiveEvent<CompressorSetupEvents>()

    private val isLoadingExample = MutableStateFlow(false)

    val state = combine(
        settings.scanPaths.flow,
        settings.compressionQuality.flow,
        settings.minAge.flow,
        settings.minSizeBytes.flow,
        compressor.progress,
        isLoadingExample,
    ) { scanPaths, quality, minAge, minSizeBytes, progress, loadingExample ->
        val jpegRatio = compressionEstimator.estimateOutputRatio(CompressibleImage.MIME_TYPE_JPEG, quality)
        val estimatedSavings = jpegRatio?.let { ((1.0 - it) * 100).toInt() }

        State(
            scanPaths = scanPaths.paths.sortedBy { it.path },
            quality = quality,
            minAge = minAge,
            minSizeBytes = minSizeBytes,
            estimatedSavingsPercent = estimatedSavings,
            progress = progress,
            isLoadingExample = loadingExample,
            canStartScan = scanPaths.paths.isNotEmpty(),
        )
    }.asLiveData2()

    data class State(
        val scanPaths: List<APath>,
        val quality: Int,
        val minAge: Duration,
        val minSizeBytes: Long,
        val estimatedSavingsPercent: Int? = null,
        val progress: Progress.Data? = null,
        val isLoadingExample: Boolean = false,
        val canStartScan: Boolean = false,
    )

    fun updateQuality(quality: Int) = launch {
        log(TAG, INFO) { "updateQuality($quality)" }
        settings.compressionQuality.value(quality)
    }

    fun updateMinAge(age: Duration) = launch {
        log(TAG, INFO) { "updateMinAge($age)" }
        settings.minAge.value(age)
    }

    fun updateMinSize(size: Long) = launch {
        log(TAG, INFO) { "updateMinSize($size)" }
        settings.minSizeBytes.value(size)
    }

    fun updatePaths(paths: Set<APath>) = launch {
        log(TAG, INFO) { "updatePaths(${paths.size} paths)" }
        settings.scanPaths.value(CompressorSettings.ScanPaths(paths = paths))
    }

    fun openPathPicker() = launch {
        log(TAG) { "openPathPicker()" }
        val currentPaths = settings.scanPaths.value().paths
        events.postValue(CompressorSetupEvents.OpenPathPicker(currentPaths))
    }

    fun startScan() = launch {
        log(TAG, INFO) { "startScan()" }

        val result = taskManager.submit(CompressorScanTask())
        log(TAG, INFO) { "Scan result: $result" }

        if (compressor.state.first().data.hasData) {
            events.postValue(CompressorSetupEvents.NavigateToList)
        } else {
            events.postValue(CompressorSetupEvents.NoResultsFound)
        }
    }

    fun showExample() = launch {
        log(TAG) { "showExample()" }
        isLoadingExample.value = true
        try {
            val quality = settings.compressionQuality.value()
            val sampleImage = findSampleImage()
            if (sampleImage != null) {
                events.postValue(CompressorSetupEvents.ShowExample(sampleImage, quality))
            } else {
                events.postValue(CompressorSetupEvents.NoExampleFound)
            }
        } finally {
            isLoadingExample.value = false
        }
    }

    private suspend fun findSampleImage(): CompressibleImage? {
        val searchPaths = settings.scanPaths.value().paths
        if (searchPaths.isEmpty()) return null

        for (searchPath in searchPaths) {
            try {
                val lookup = searchPath.walk(
                    gatewaySwitch,
                    options = APathGateway.WalkOptions()
                ).firstOrNull { lookup ->
                    if (!lookup.isFile) return@firstOrNull false
                    if (lookup.size < settings.minSizeBytes.value()) return@firstOrNull false
                    val mimeType = mimeTypeTool.determineMimeType(lookup)
                    mimeType in CompressibleImage.SUPPORTED_MIME_TYPES
                }

                if (lookup != null) {
                    val mimeType = mimeTypeTool.determineMimeType(lookup)
                    val quality = settings.compressionQuality.value()
                    val estimatedCompressedSize = compressionEstimator.estimateCompressedSize(
                        lookup.size,
                        mimeType,
                        quality,
                    )
                    return CompressibleImage(
                        lookup = lookup,
                        mimeType = mimeType,
                        estimatedCompressedSize = estimatedCompressedSize,
                        wasCompressedBefore = false,
                    )
                }
            } catch (e: Exception) {
                log(TAG) { "Failed to search path $searchPath: ${e.message}" }
            }
        }
        return null
    }

    companion object {
        private val TAG = logTag("Compressor", "Setup", "ViewModel")
    }
}
