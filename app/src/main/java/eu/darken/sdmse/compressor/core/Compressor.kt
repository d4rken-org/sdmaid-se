package eu.darken.sdmse.compressor.core

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.withProgress
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.sharedresource.keepResourceHoldersAlive
import eu.darken.sdmse.compressor.core.processor.ImageProcessor
import eu.darken.sdmse.compressor.core.scanner.ImageScanner
import eu.darken.sdmse.compressor.core.tasks.CompressorProcessTask
import eu.darken.sdmse.compressor.core.tasks.CompressorScanTask
import eu.darken.sdmse.compressor.core.tasks.CompressorTask
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import eu.darken.sdmse.main.core.SDMTool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class Compressor @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val gatewaySwitch: GatewaySwitch,
    private val exclusionManager: ExclusionManager,
    private val scanner: Provider<ImageScanner>,
    private val processor: Provider<ImageProcessor>,
    private val settings: CompressorSettings,
) : SDMTool, Progress.Client {

    override val type: SDMTool.Type = SDMTool.Type.COMPRESSOR

    override val sharedResource = SharedResource.createKeepAlive(TAG, appScope)

    private val progressPub = MutableStateFlow<Progress.Data?>(null)
    override val progress: Flow<Progress.Data?> = progressPub
    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    private val internalData = MutableStateFlow(null as Data?)
    private val lastResult = MutableStateFlow<CompressorTask.Result?>(null)

    override val state: Flow<State> = combine(
        internalData,
        lastResult,
        progress,
    ) { data, lastResult, progress ->
        State(
            data = data,
            lastResult = lastResult,
            progress = progress,
        )
    }.replayingShare(appScope)

    private val toolLock = Mutex()
    override suspend fun submit(task: SDMTool.Task): SDMTool.Task.Result = toolLock.withLock {
        task as CompressorTask
        log(TAG, INFO) { "submit($task) starting..." }
        updateProgress { Progress.Data() }

        try {
            val result = keepResourceHoldersAlive(gatewaySwitch) {
                when (task) {
                    is CompressorScanTask -> performScan(task)
                    is CompressorProcessTask -> performProcess(task)
                }
            }
            lastResult.value = result
            log(TAG, INFO) { "submit($task) finished: $result" }
            result
        } catch (e: CancellationException) {
            throw e
        } finally {
            updateProgress { null }
        }
    }

    private suspend fun performScan(
        task: CompressorScanTask = CompressorScanTask()
    ): CompressorScanTask.Result {
        log(TAG) { "performScan(): $task" }
        internalData.value = null

        val enabledMimeTypes = buildSet {
            if (settings.includeJpeg.value()) add(CompressibleImage.MIME_TYPE_JPEG)
            if (settings.includeWebp.value()) add(CompressibleImage.MIME_TYPE_WEBP)
        }

        val scanOptions = ImageScanner.Options(
            paths = task.paths ?: settings.scanPaths.value().paths,
            minimumSize = settings.minSizeBytes.value(),
            minAgeDays = settings.minAgeDays.value(),
            enabledMimeTypes = enabledMimeTypes,
            skipPreviouslyCompressed = settings.skipPreviouslyCompressed.value(),
            compressionQuality = settings.compressionQuality.value(),
        )

        val results = scanner.get().withProgress(this) {
            scan(scanOptions)
        }

        log(TAG, INFO) { "performScan(): ${results.size} images found" }

        internalData.value = Data(
            images = results
        )

        return CompressorScanTask.Success(
            itemCount = results.size,
            totalSize = results.sumOf { it.size },
            estimatedSavings = results.sumOf { it.estimatedSavings ?: 0L },
        )
    }

    private suspend fun performProcess(
        task: CompressorProcessTask = CompressorProcessTask()
    ): CompressorProcessTask.Success {
        log(TAG) { "performProcess(): $task" }

        val snapshot = internalData.value ?: throw IllegalStateException("No scan data available")

        val quality = task.qualityOverride ?: settings.compressionQuality.value()

        val result = processor.get().withProgress(this) {
            process(task, snapshot, quality)
        }

        updateProgress { Progress.Data() }

        internalData.value = snapshot.prune(result.success.map { it.identifier }.toSet())

        return CompressorProcessTask.Success(
            affectedSpace = result.savedSpace,
            affectedPaths = result.success.map { it.path }.toSet(),
            processedCount = result.success.size,
        )
    }

    suspend fun exclude(
        identifiers: Collection<CompressibleMedia.Id>
    ): Unit = toolLock.withLock {
        log(TAG) { "exclude(): $identifiers" }

        val snapshot = internalData.value ?: return@withLock

        val exclusions = identifiers
            .mapNotNull { id -> snapshot.images.find { it.identifier == id } }
            .map { media ->
                PathExclusion(
                    path = media.path,
                    tags = setOf(Exclusion.Tag.COMPRESSOR),
                )
            }
            .toSet()
        exclusionManager.save(exclusions)

        internalData.value = snapshot.copy(
            images = snapshot.images.filter { !identifiers.contains(it.identifier) }.toSet()
        )
    }

    data class State(
        val data: Data?,
        val progress: Progress.Data?,
        val lastResult: CompressorTask.Result? = null,
    ) : SDMTool.State

    data class Data(
        val images: Set<CompressibleImage> = emptySet(),
    ) {
        val totalSize: Long get() = images.sumOf { it.size }
        val estimatedSavings: Long get() = images.sumOf { it.estimatedSavings ?: 0L }
        val totalCount: Int get() = images.size
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Compressor): SDMTool
    }

    companion object {
        internal val TAG = logTag("Compressor")
    }
}

internal fun Compressor.Data.prune(processedIds: Set<CompressibleMedia.Id>): Compressor.Data {
    val newImages = this.images
        .filter { image ->
            val wasProcessed = processedIds.contains(image.identifier)
            if (wasProcessed) log(Compressor.TAG) { "Prune: Processed image: $image" }
            !wasProcessed
        }
        .toSet()

    return this.copy(images = newImages)
}
