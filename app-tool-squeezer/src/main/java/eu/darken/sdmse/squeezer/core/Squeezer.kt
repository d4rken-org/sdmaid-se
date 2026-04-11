package eu.darken.sdmse.squeezer.core

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.withProgress
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.sharedresource.keepResourceHoldersAlive
import eu.darken.sdmse.squeezer.core.processor.ImageProcessor
import eu.darken.sdmse.squeezer.core.processor.VideoProcessor
import eu.darken.sdmse.squeezer.core.scanner.MediaScanner
import eu.darken.sdmse.squeezer.core.tasks.SqueezerProcessTask
import eu.darken.sdmse.squeezer.core.tasks.SqueezerScanTask
import eu.darken.sdmse.squeezer.core.tasks.SqueezerTask
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
class Squeezer @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val gatewaySwitch: GatewaySwitch,
    private val exclusionManager: ExclusionManager,
    private val scanner: Provider<MediaScanner>,
    private val imageProcessor: Provider<ImageProcessor>,
    private val videoProcessor: Provider<VideoProcessor>,
    private val settings: SqueezerSettings,
) : SDMTool, Progress.Client {

    override val type: SDMTool.Type = SDMTool.Type.SQUEEZER

    override val sharedResource = SharedResource.createKeepAlive(TAG, appScope)

    private val progressPub = MutableStateFlow<Progress.Data?>(null)
    override val progress: Flow<Progress.Data?> = progressPub
    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    private val internalData = MutableStateFlow(null as Data?)
    private val lastResult = MutableStateFlow<SqueezerTask.Result?>(null)

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
        task as SqueezerTask
        log(TAG, INFO) { "submit($task) starting..." }
        updateProgress { Progress.Data() }

        try {
            val result = keepResourceHoldersAlive(gatewaySwitch) {
                when (task) {
                    is SqueezerScanTask -> performScan(task)
                    is SqueezerProcessTask -> performProcess(task)
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
        task: SqueezerScanTask = SqueezerScanTask()
    ): SqueezerScanTask.Result {
        log(TAG) { "performScan(): $task" }
        internalData.value = null

        val enabledMimeTypes = buildSet {
            if (settings.includeJpeg.value()) add(CompressibleImage.MIME_TYPE_JPEG)
            if (settings.includeWebp.value()) add(CompressibleImage.MIME_TYPE_WEBP)
            if (settings.includeVideo.value()) add(CompressibleVideo.MIME_TYPE_MP4)
        }

        val scanOptions = MediaScanner.Options(
            paths = task.paths ?: settings.scanPaths.value().paths,
            minimumSize = settings.minSizeBytes.value(),
            minAge = settings.minAge.value(),
            enabledMimeTypes = enabledMimeTypes,
            skipPreviouslyCompressed = settings.skipPreviouslyCompressed.value(),
            compressionQuality = settings.compressionQuality.value(),
        )

        val scanResult = scanner.get().withProgress(this) {
            scan(scanOptions)
        }
        val results = scanResult.items

        log(TAG, INFO) {
            "performScan(): ${results.size} media items found, " +
                    "${scanResult.skippedInaccessibleCount} skipped (inaccessible)"
        }

        internalData.value = Data(
            media = results,
        )

        return SqueezerScanTask.Success(
            itemCount = results.size,
            totalSize = results.sumOf { it.size },
            estimatedSavings = results.sumOf { it.estimatedSavings ?: 0L },
            skippedInaccessibleCount = scanResult.skippedInaccessibleCount,
        )
    }

    private suspend fun performProcess(
        task: SqueezerProcessTask = SqueezerProcessTask()
    ): SqueezerProcessTask.Success {
        log(TAG) { "performProcess(): $task" }

        val snapshot = internalData.value ?: throw IllegalStateException("No scan data available")

        val quality = task.qualityOverride ?: settings.compressionQuality.value()

        val targets = when (val mode = task.mode) {
            is SqueezerProcessTask.TargetMode.All -> snapshot.media
            is SqueezerProcessTask.TargetMode.Selected -> {
                snapshot.media.filter { mode.targets.contains(it.identifier) }.toSet()
            }
        }

        val imageTargets = targets.filterIsInstance<CompressibleImage>().toSet()
        val videoTargets = targets.filterIsInstance<CompressibleVideo>().toSet()

        val imageResult = if (imageTargets.isNotEmpty()) {
            imageProcessor.get().withProgress(this) { process(imageTargets, quality) }
        } else {
            ImageProcessor.Result(success = emptySet(), failed = emptyMap(), savedSpace = 0L)
        }

        val videoResult = if (videoTargets.isNotEmpty()) {
            videoProcessor.get().withProgress(this) { process(videoTargets, quality) }
        } else {
            VideoProcessor.Result(success = emptySet(), failed = emptyMap(), savedSpace = 0L)
        }

        val allSuccess: Set<CompressibleMedia> = imageResult.success + videoResult.success
        val totalSavedSpace = imageResult.savedSpace + videoResult.savedSpace

        val allFailures = imageResult.failed.values + videoResult.failed.values
        val failureReasons: Map<FailureReason, Int> = allFailures
            .map { it.toFailureReason() }
            .groupingBy { it }
            .eachCount()

        updateProgress { Progress.Data() }

        internalData.value = snapshot.prune(allSuccess.map { it.identifier }.toSet())

        return SqueezerProcessTask.Success(
            affectedSpace = totalSavedSpace,
            affectedPaths = allSuccess.map { it.path }.toSet(),
            processedCount = allSuccess.size,
            failedCount = allFailures.size,
            failureReasons = failureReasons,
        )
    }

    suspend fun exclude(
        identifiers: Collection<CompressibleMedia.Id>
    ): Unit = toolLock.withLock {
        log(TAG) { "exclude(): $identifiers" }

        val snapshot = internalData.value ?: return@withLock

        val exclusions = identifiers
            .mapNotNull { id -> snapshot.media.find { it.identifier == id } }
            .map { media ->
                PathExclusion(
                    path = media.path,
                    tags = setOf(Exclusion.Tag.SQUEEZER),
                )
            }
            .toSet()
        exclusionManager.save(exclusions)

        internalData.value = snapshot.copy(
            media = snapshot.media.filter { !identifiers.contains(it.identifier) }.toSet()
        )
    }

    data class State(
        val data: Data?,
        val progress: Progress.Data?,
        val lastResult: SqueezerTask.Result? = null,
    ) : SDMTool.State

    data class Data(
        val media: Set<CompressibleMedia> = emptySet(),
    ) {
        val images: Set<CompressibleImage> get() = media.filterIsInstance<CompressibleImage>().toSet()
        val videos: Set<CompressibleVideo> get() = media.filterIsInstance<CompressibleVideo>().toSet()
        val totalSize: Long get() = media.sumOf { it.size }
        val estimatedSavings: Long get() = media.sumOf { it.estimatedSavings ?: 0L }
        val totalCount: Int get() = media.size
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Squeezer): SDMTool
    }

    companion object {
        internal val TAG = logTag("Squeezer")
    }
}

internal fun Squeezer.Data.prune(processedIds: Set<CompressibleMedia.Id>): Squeezer.Data {
    val newMedia = this.media
        .filter { item ->
            val wasProcessed = processedIds.contains(item.identifier)
            if (wasProcessed) log(Squeezer.TAG) { "Prune: Processed item: $item" }
            !wasProcessed
        }
        .toSet()

    return this.copy(media = newMedia)
}
