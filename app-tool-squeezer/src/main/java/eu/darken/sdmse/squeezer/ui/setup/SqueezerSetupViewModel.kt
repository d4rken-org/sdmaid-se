package eu.darken.sdmse.squeezer.ui.setup

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.MimeTypeTool
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathGateway
import eu.darken.sdmse.common.files.isFile
import eu.darken.sdmse.common.files.local.LocalGateway
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.flow.SingleEventFlow
import eu.darken.sdmse.common.flow.combine
import eu.darken.sdmse.common.navigation.NavigationController
import eu.darken.sdmse.common.picker.PickerRequest
import eu.darken.sdmse.common.picker.PickerResultKey
import eu.darken.sdmse.common.picker.PickerRoute
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.storage.PathMapper
import eu.darken.sdmse.common.storage.StorageEnvironment
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import eu.darken.sdmse.squeezer.core.CompressibleImage
import eu.darken.sdmse.squeezer.core.CompressionEstimator
import eu.darken.sdmse.squeezer.core.Squeezer
import eu.darken.sdmse.squeezer.core.SqueezerEligibility
import eu.darken.sdmse.squeezer.core.SqueezerPathNormalizer
import eu.darken.sdmse.squeezer.core.SqueezerSettings
import eu.darken.sdmse.squeezer.core.hasData
import eu.darken.sdmse.squeezer.core.tasks.SqueezerScanTask
import eu.darken.sdmse.squeezer.ui.SqueezerListRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
    @Suppress("unused") private val storageEnvironment: StorageEnvironment,
    private val mimeTypeTool: MimeTypeTool,
    private val navCtrl: NavigationController,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    val events = SingleEventFlow<Event>()

    private val isLoadingExample = MutableStateFlow(false)

    init {
        navCtrl.consumeResults(PickerResultKey(PICKER_REQUEST_KEY))
            .onEach { result ->
                log(TAG, INFO) { "Picker returned ${result.selectedPaths.size} paths" }
                updatePaths(result.selectedPaths)
            }
            .launchIn(vmScope)
    }

    val state: StateFlow<State> = combine(
        settings.scanPaths.flow,
        settings.compressionQuality.flow,
        settings.minAge.flow,
        settings.minSizeBytes.flow,
        squeezer.progress,
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
    }.safeStateIn(
        initialValue = State(),
        onError = { State() },
    )

    data class State(
        val scanPaths: List<APath> = emptyList(),
        val quality: Int = SqueezerSettings.DEFAULT_QUALITY,
        val minAge: Duration = SqueezerSettings.MIN_AGE_DEFAULT,
        val minSizeBytes: Long = SqueezerSettings.MIN_FILE_SIZE,
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

        // Squeezer can only process files reachable via java.io.File (Transformer +
        // BitmapFactory hard-require raw paths). SAF roots on primary storage can usually
        // be mapped back to a LocalPath via PathMapper; anything that can't be mapped is
        // surfaced to the UI so the user isn't left wondering why their scan returned
        // nothing.
        val normalized = SqueezerPathNormalizer.normalize(paths, pathMapper)

        if (normalized.dropped.isNotEmpty()) {
            log(TAG, WARN) { "Dropped ${normalized.dropped.size} non-local paths: ${normalized.dropped}" }
            events.tryEmit(Event.PathsDropped(normalized.dropped))
        }

        settings.scanPaths.value(SqueezerSettings.ScanPaths(paths = normalized.accepted))
    }

    fun openPathPicker() = launch {
        log(TAG) { "openPathPicker()" }
        val current = settings.scanPaths.value().paths
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
                    selectedPaths = current.toList(),
                ),
            ),
        )
    }

    fun startScan() = launch {
        log(TAG, INFO) { "startScan()" }

        val result = taskSubmitter.submit(SqueezerScanTask())
        log(TAG, INFO) { "Scan result: $result" }

        if (squeezer.state.first().data.hasData) {
            navTo(SqueezerListRoute)
        } else {
            events.tryEmit(Event.NoResultsFound)
        }
    }

    fun showExample() = launch {
        log(TAG) { "showExample()" }
        isLoadingExample.value = true
        try {
            val quality = settings.compressionQuality.value()
            val sampleImage = findSampleImage()
            if (sampleImage != null) {
                events.tryEmit(Event.ShowExample(sampleImage, quality))
            } else {
                events.tryEmit(Event.NoExampleFound)
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

    sealed interface Event {
        data class ShowExample(val sampleImage: CompressibleImage, val quality: Int) : Event
        data object NoExampleFound : Event
        data object NoResultsFound : Event
        data class PathsDropped(val droppedPaths: Set<APath>) : Event
    }

    companion object {
        internal const val PICKER_REQUEST_KEY = "squeezer.setup.paths"
        private val TAG = logTag("Squeezer", "Setup", "ViewModel")
    }
}
