package eu.darken.sdmse.swiper.core.scanner

import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathGateway
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.isFile
import eu.darken.sdmse.common.files.walk
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.updateProgressCount
import eu.darken.sdmse.common.progress.updateProgressPrimary
import eu.darken.sdmse.common.progress.updateProgressSecondary
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.pathExclusions
import eu.darken.sdmse.exclusion.core.types.match
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.swiper.core.SessionState
import eu.darken.sdmse.swiper.core.db.SwipeItemEntity
import eu.darken.sdmse.swiper.core.db.SwipeSessionEntity
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.take
import kotlin.coroutines.coroutineContext
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class SwiperScanner @Inject constructor(
    private val exclusionManager: ExclusionManager,
    private val dispatcherProvider: DispatcherProvider,
    private val gatewaySwitch: GatewaySwitch,
) : Progress.Host, Progress.Client {

    private val progressPub = MutableStateFlow<Progress.Data?>(Progress.Data())
    override val progress: Flow<Progress.Data?> = progressPub.throttleLatest(250)

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    data class Options(
        val paths: Set<APath>,
        val itemLimit: Int?,
    )

    data class Result(
        val session: SwipeSessionEntity,
        val items: List<SwipeItemEntity>,
        val lookups: List<APathLookup<*>>,
    )

    suspend fun scan(options: Options): Result {
        log(TAG) { "scan($options)" }

        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_searching)

        val exclusions = exclusionManager.pathExclusions(SDMTool.Type.SWIPER)
        log(TAG) { "Swiper exclusions are: $exclusions" }

        val searchFlow = options.paths.asFlow()
            .flatMapMerge(2) { path ->
                val filter: suspend (APathLookup<*>) -> Boolean = filter@{ toCheck: APathLookup<*> ->
                    exclusions.none { it.match(toCheck) }
                }

                val lookup = gatewaySwitch.lookup(path)
                if (lookup.fileType == FileType.FILE) {
                    if (filter(lookup)) flowOf(lookup) else emptyFlow()
                } else {
                    path.walk(
                        gatewaySwitch,
                        options = APathGateway.WalkOptions(
                            onFilter = filter,
                        ),
                    )
                }
            }
            .flowOn(dispatcherProvider.IO)
            .buffer(1024)
            .filter { it.isFile }

        var count = 0
        val files = mutableListOf<APathLookup<*>>()
        val limitedFlow = if (options.itemLimit != null) searchFlow.take(options.itemLimit) else searchFlow

        limitedFlow.collect { lookup ->
            currentCoroutineContext().ensureActive()
            count++
            files.add(lookup)
            updateProgressSecondary(lookup.userReadablePath)
            updateProgressCount(Progress.Count.Counter(count))
            log(TAG, VERBOSE) { "Found file #$count: ${lookup.path}" }
        }

        // Sort by modification time so users start with oldest files first
        files.sortBy { it.modifiedAt }

        log(TAG) { "Scan found ${files.size} files" }

        val sessionId = UUID.randomUUID().toString()
        val now = Instant.now()

        val session = SwipeSessionEntity(
            sessionId = sessionId,
            sourcePaths = options.paths.toList(),
            currentIndex = 0,
            totalItems = files.size,
            createdAt = now,
            lastModifiedAt = now,
            state = SessionState.READY,
        )

        val items = files.mapIndexed { index, lookup ->
            SwipeItemEntity(
                sessionId = sessionId,
                itemIndex = index,
                path = lookup.lookedUp,
            )
        }

        return Result(
            session = session,
            items = items,
            lookups = files,
        )
    }

    companion object {
        private val TAG = logTag("Swiper", "Scanner")
    }
}
