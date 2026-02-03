package eu.darken.sdmse.compressor.core.scanner

import eu.darken.sdmse.common.MimeTypeTool
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathGateway
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.isFile
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.walk
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.updateProgressCount
import eu.darken.sdmse.common.progress.updateProgressPrimary
import eu.darken.sdmse.common.progress.updateProgressSecondary
import eu.darken.sdmse.common.storage.StorageEnvironment
import eu.darken.sdmse.compressor.core.CompressibleImage
import eu.darken.sdmse.compressor.core.CompressionEstimator
import eu.darken.sdmse.compressor.core.CompressorSettings
import eu.darken.sdmse.compressor.core.history.CompressionHistoryDatabase
import eu.darken.sdmse.compressor.core.processor.ExifPreserver
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.pathExclusions
import eu.darken.sdmse.exclusion.core.types.match
import eu.darken.sdmse.main.core.SDMTool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.time.Duration
import java.time.Instant
import javax.inject.Inject


class ImageScanner @Inject constructor(
    private val exclusionManager: ExclusionManager,
    private val dispatcherProvider: DispatcherProvider,
    private val gatewaySwitch: GatewaySwitch,
    private val mimeTypeTool: MimeTypeTool,
    private val storageEnvironment: StorageEnvironment,
    private val historyDatabase: CompressionHistoryDatabase,
    private val compressionEstimator: CompressionEstimator,
    private val exifPreserver: ExifPreserver,
    private val settings: CompressorSettings,
) : Progress.Host, Progress.Client {

    private val progressPub = MutableStateFlow<Progress.Data?>(Progress.Data())
    override val progress: Flow<Progress.Data?> = progressPub.throttleLatest(250)

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    data class Options(
        val paths: Set<APath>,
        val minimumSize: Long,
        val minAge: Duration?,
        val enabledMimeTypes: Set<String>,
        val skipPreviouslyCompressed: Boolean,
        val compressionQuality: Int,
    )

    private suspend fun createSearchFlow(paths: Set<APath>): Flow<APathLookup<*>> {
        val exclusions = exclusionManager.pathExclusions(SDMTool.Type.COMPRESSOR)
        log(TAG) { "Compressor exclusions are: $exclusions" }
        log(TAG) { "Search paths: $paths" }

        return paths.asFlow()
            .flatMapMerge(2) { path ->
                val filter: suspend (APathLookup<*>) -> Boolean = filter@{ toCheck: APathLookup<*> ->
                    exclusions.none { it.match(toCheck) }
                }
                path.walk(
                    gatewaySwitch,
                    options = APathGateway.WalkOptions(
                        onFilter = filter
                    )
                )
            }
    }

    private fun getDefaultSearchPaths(): Set<APath> {
        return storageEnvironment.externalDirs
            .map { it.child("DCIM") }
            .toSet()
    }

    suspend fun scan(options: Options): Set<CompressibleImage> {
        log(TAG) { "scan($options)" }

        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_searching)
        updateProgressSecondary()
        updateProgressCount(Progress.Count.Indeterminate())

        val searchPaths = options.paths.ifEmpty { getDefaultSearchPaths() }
        val searchFlow = createSearchFlow(searchPaths)
            .flowOn(dispatcherProvider.IO)
            .buffer(1024)
            .filter { lookup ->
                if (!lookup.isFile) return@filter false

                val isGoodSize = lookup.size >= options.minimumSize
                if (!isGoodSize) {
                    if (Bugs.isTrace) log(TAG, VERBOSE) { "Skipping (size): $lookup" }
                    return@filter false
                }

                options.minAge?.let { minAge ->
                    val cutoff = Instant.now().minus(minAge)
                    // Skip if file is NEWER than cutoff (we want older files)
                    if (lookup.modifiedAt.isAfter(cutoff)) {
                        if (Bugs.isTrace) log(TAG, VERBOSE) { "Skipping (too recent): $lookup" }
                        return@filter false
                    }
                }

                true
            }

        var scannedCount = 0
        val images = mutableSetOf<CompressibleImage>()

        searchFlow.collect { lookup ->
            scannedCount++
            updateProgressCount(Progress.Count.Counter(scannedCount))
            updateProgressSecondary(lookup.userReadablePath)

            val mimeType = mimeTypeTool.determineMimeType(lookup)

            if (mimeType !in CompressibleImage.SUPPORTED_MIME_TYPES) {
                if (Bugs.isTrace) log(TAG, VERBOSE) { "Skipping (mime): $mimeType - $lookup" }
                return@collect
            }

            if (mimeType !in options.enabledMimeTypes) {
                if (Bugs.isTrace) log(TAG, VERBOSE) { "Skipping (type disabled): $mimeType - $lookup" }
                return@collect
            }

            val wasCompressedBefore = if (options.skipPreviouslyCompressed) {
                // Check EXIF marker first (fast - only reads file header)
                val hasExifMarker = if (settings.writeExifMarker.value()) {
                    try {
                        val localPath = lookup.lookedUp as? LocalPath
                        localPath?.let { exifPreserver.hasCompressionMarker(File(it.path)) } ?: false
                    } catch (e: Exception) {
                        log(TAG, WARN) { "Failed to check EXIF marker for $lookup: ${e.message}" }
                        false
                    }
                } else {
                    false
                }

                // If no EXIF marker, check database hash (slow - reads entire file)
                if (!hasExifMarker) {
                    try {
                        val contentHash = historyDatabase.computeContentHash(lookup.lookedUp)
                        historyDatabase.hasBeenCompressed(contentHash)
                    } catch (e: Exception) {
                        log(TAG, WARN) { "Failed to compute content hash for $lookup: ${e.message}" }
                        false
                    }
                } else {
                    true
                }
            } else {
                false
            }

            if (wasCompressedBefore) {
                log(TAG, VERBOSE) { "Skipping previously compressed: $lookup" }
                return@collect
            }

            val estimatedCompressedSize = estimateCompressedSize(lookup, mimeType, options.compressionQuality)

            val image = CompressibleImage(
                lookup = lookup,
                mimeType = mimeType,
                estimatedCompressedSize = estimatedCompressedSize,
                wasCompressedBefore = wasCompressedBefore,
            )

            images.add(image)
            log(TAG, VERBOSE) { "Found compressible image: $image" }
        }

        log(TAG) { "Scan complete: ${images.size} compressible images found" }
        return images
    }

    private fun estimateCompressedSize(lookup: APathLookup<*>, mimeType: String, quality: Int): Long? {
        return compressionEstimator.estimateCompressedSize(lookup.size, mimeType, quality)
    }

    companion object {
        private val TAG = logTag("Compressor", "Scanner")
    }
}
