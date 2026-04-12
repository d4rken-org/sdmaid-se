package eu.darken.sdmse.squeezer.ui.setup

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
import eu.darken.sdmse.common.files.isFile
import eu.darken.sdmse.common.files.local.LocalGateway
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.flow.combine
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.storage.PathMapper
import eu.darken.sdmse.common.storage.StorageEnvironment
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.squeezer.core.CompressibleImage
import eu.darken.sdmse.squeezer.core.CompressionEstimator
import eu.darken.sdmse.squeezer.core.Squeezer
import eu.darken.sdmse.squeezer.core.SqueezerEligibility
import eu.darken.sdmse.squeezer.core.SqueezerPathNormalizer
import eu.darken.sdmse.squeezer.core.SqueezerSettings
import eu.darken.sdmse.squeezer.core.hasData
import eu.darken.sdmse.squeezer.core.tasks.SqueezerScanTask
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import java.time.Duration
import javax.inject.Inject

@HiltViewModel
class SqueezerSetupViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val settings: SqueezerSettings,
    private val squeezer: Squeezer,
    private val taskSubmitter: TaskSubmitter,
    private val compressionEstimator: CompressionEstimator,
    private val localGateway: LocalGateway,
    private val pathMapper: PathMapper,
    private val storageEnvironment: StorageEnvironment,
    private val mimeTypeTool: MimeTypeTool,
) : ViewModel3(dispatcherProvider) {

    val events = SingleLiveEvent<SqueezerSetupEvents>()

    private val isLoadingExample = MutableStateFlow(false)

    val state = combine(
        settings.scanPaths.flow,
        settings.compressionQuality.flow,
        settings.minAge.flow,
        squeezer.progress,
        isLoadingExample,
    ) { scanPaths, quality, minAge, progress, loadingExample ->
        val jpegRatio = compressionEstimator.estimateOutputRatio(CompressibleImage.MIME_TYPE_JPEG, quality)
        val estimatedSavings = jpegRatio?.let { ((1.0 - it) * 100).toInt() }

        State(
            scanPaths = scanPaths.paths.sortedBy { it.path },
            quality = quality,
            minAge = minAge,
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

    fun updatePaths(paths: Set<APath>) = launch {
        log(TAG, INFO) { "updatePaths(${paths.size} paths)" }

        // Squeezer can only process files reachable via java.io.File (Transformer +
        // BitmapFactory hard-require raw paths). SAF roots on primary storage can usually
        // be mapped back to a LocalPath via PathMapper; anything that can't be mapped is
        // surfaced to the UI so the user isn't left wondering why their scan returned
        // nothing. The pure normalization logic lives in SqueezerPathNormalizer so it can
        // be unit-tested without the ViewModel harness.
        val normalized = SqueezerPathNormalizer.normalize(paths, pathMapper)

        if (normalized.dropped.isNotEmpty()) {
            log(TAG, WARN) { "Dropped ${normalized.dropped.size} non-local paths: ${normalized.dropped}" }
            events.postValue(SqueezerSetupEvents.PathsDropped(normalized.dropped))
        }

        settings.scanPaths.value(SqueezerSettings.ScanPaths(paths = normalized.accepted))
    }

    fun openPathPicker() = launch {
        log(TAG) { "openPathPicker()" }
        val currentPaths = settings.scanPaths.value().paths
        events.postValue(SqueezerSetupEvents.OpenPathPicker(currentPaths))
    }

    fun startScan() = launch {
        log(TAG, INFO) { "startScan()" }

        val result = taskSubmitter.submit(SqueezerScanTask())
        log(TAG, INFO) { "Scan result: $result" }

        if (squeezer.state.first().data.hasData) {
            events.postValue(SqueezerSetupEvents.NavigateToList)
        } else {
            events.postValue(SqueezerSetupEvents.NoResultsFound)
        }
    }

    fun showExample() = launch {
        log(TAG) { "showExample()" }
        isLoadingExample.value = true
        try {
            val quality = settings.compressionQuality.value()
            val sampleImage = findSampleImage()
            if (sampleImage != null) {
                events.postValue(SqueezerSetupEvents.ShowExample(sampleImage, quality))
            } else {
                events.postValue(SqueezerSetupEvents.NoExampleFound)
            }
        } finally {
            isLoadingExample.value = false
        }
    }

    private suspend fun findSampleImage(): CompressibleImage? {
        val searchPaths = settings.scanPaths.value().paths
        if (searchPaths.isEmpty()) return null

        for (searchPath in searchPaths) {
            val localPath = searchPath as? LocalPath ?: continue
            try {
                val lookup = localGateway.walk(
                    path = localPath,
                    options = APathGateway.WalkOptions(),
                    mode = LocalGateway.Mode.NORMAL,
                ).firstOrNull { lookup ->
                    if (!lookup.isFile) return@firstOrNull false
                    if (SqueezerEligibility.check(lookup.lookedUp) != SqueezerEligibility.Verdict.OK) {
                        return@firstOrNull false
                    }
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
        private val TAG = logTag("Squeezer", "Setup", "ViewModel")
    }
}
