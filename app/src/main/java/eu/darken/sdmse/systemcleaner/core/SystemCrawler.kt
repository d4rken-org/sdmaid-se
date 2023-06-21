package eu.darken.sdmse.systemcleaner.core

import dagger.Reusable
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.areas.currentAreas
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.Segments
import eu.darken.sdmse.common.files.isAncestorOf
import eu.darken.sdmse.common.files.segs
import eu.darken.sdmse.common.files.startsWith
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
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilter
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilterException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import java.io.IOException
import javax.inject.Inject

@Reusable
class SystemCrawler @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val filterFactories: Set<@JvmSuppressWildcards SystemCleanerFilter.Factory>,
    private val areaManager: DataAreaManager,
    private val gatewaySwitch: GatewaySwitch,
    private val exclusionManager: ExclusionManager,
) : Progress.Host, Progress.Client {

    private val progressPub = MutableStateFlow<Progress.Data?>(Progress.DEFAULT_STATE)
    override val progress: Flow<Progress.Data?> = progressPub.throttleLatest(250)

    init {
        filterFactories.forEach { log(TAG) { "Available filter: $it" } }
    }

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    suspend fun crawl(): Collection<FilterContent> {
        log(TAG) { "crawl()" }
        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_searching)
        updateProgressSecondary(eu.darken.sdmse.common.R.string.general_progress_generating_searchpaths)
        updateProgressCount(Progress.Count.Indeterminate())

        val pathExclusions = exclusionManager.pathExclusions(SDMTool.Type.SYSTEMCLEANER)

        val filters = filterFactories
            .asFlow()
            .filter { it.isEnabled() }
            .map { it.create() }
            .onEach {
                log(TAG) { "Initializing $it" }
                it.initialize()
            }
            .toList()

        val currentAreas = areaManager.currentAreas()
        val targetAreas = filters
            .map { it.targetAreas() }
            .flatten()
            .toSet()
            .map { type -> currentAreas.filter { it.type == type } }
            .flatten()
            .toSet()
        log(TAG) { "Target areas wanted by filters: $targetAreas" }

        val sdcardChildOverLaps = setOf(DataArea.Type.PUBLIC_DATA, DataArea.Type.PUBLIC_MEDIA, DataArea.Type.PUBLIC_OBB)
        val sdcardOverlaps = currentAreas
            .filter { sdcardChildOverLaps.contains(it.type) }
            .map { it.path }

        val skipSegments = mutableSetOf<Segments>()
        if (targetAreas.all { it.path.segments != segs("", "data", "data") }) {
            skipSegments.add(segs("", "data", "data"))
            skipSegments.add(segs("", "data", "user", "0"))
        }
        skipSegments.add(segs("", "data", "media", "0"))
        log(TAG) { "Skip segments: $skipSegments" }

        val sieveContents = mutableMapOf<SystemCleanerFilter, Set<APathLookup<*>>>()

        gatewaySwitch.useRes {
            targetAreas
                .asFlow()
                .flowOn(dispatcherProvider.IO)
                .flatMapMerge(3) { area ->
                    val filter: suspend (APathLookup<*>) -> Boolean = when (area.type) {
                        DataArea.Type.SDCARD -> filter@{ toCheck: APathLookup<*> ->
                            if (sdcardOverlaps.any { it.isAncestorOf(toCheck) }) return@filter false
                            pathExclusions.none { it.match(toCheck) }
                        }
                        else -> filter@{ toCheck: APathLookup<*> ->
                            if (skipSegments.any { toCheck.segments.startsWith(it) }) {
                                log(TAG, WARN) { "Skipping: $toCheck" }
                                return@filter false
                            }
                            pathExclusions.none { it.match(toCheck) }
                        }
                    }
                    area.path.walk(gatewaySwitch, filter).map { area to it }
                }
                .buffer(1024)
                .collect { (area, item) ->
                    if (Bugs.isTrace) log(TAG, VERBOSE) { "Trying to match $item" }
                    updateProgressSecondary(item.path)
                    val matched = filters
                        .filter { it.targetAreas().contains(area.type) }
                        .firstOrNull {
                            try {
                                it.matches(item)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: IOException) {
                                log(TAG, WARN) { "IO error while matching ($it): ${e.asLog()}" }
                                false
                            } catch (e: Exception) {
                                log(TAG, ERROR) { "Sieve failed ($it): ${e.asLog()}" }
                                throw SystemCleanerFilterException(it, e)
                            }
                        }

                    if (matched != null) {
                        log(TAG, INFO) { "$matched matched $item" }
                        sieveContents[matched] = (sieveContents[matched] ?: emptySet()).plus(item)
                    }
                }
        }

        return sieveContents.map { entry ->
            log(TAG, INFO) { "${entry.key} has ${entry.value.size} matches." }
            FilterContent(
                identifier = entry.key.filterIdentifier,
                items = entry.value,
            )
        }
    }

    companion object {
        private val TAG = logTag("SystemCleaner", "Crawler")
    }
}