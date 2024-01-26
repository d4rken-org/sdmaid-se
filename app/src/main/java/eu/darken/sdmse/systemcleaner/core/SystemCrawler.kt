package eu.darken.sdmse.systemcleaner.core

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
import eu.darken.sdmse.common.files.APathGateway
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.Segments
import eu.darken.sdmse.common.files.endsWith
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
import eu.darken.sdmse.systemcleaner.core.filter.FilterIdentifier
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilter
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilterException
import eu.darken.sdmse.systemcleaner.core.filter.excludeNestedLookups
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

class SystemCrawler @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val areaManager: DataAreaManager,
    private val gatewaySwitch: GatewaySwitch,
    private val exclusionManager: ExclusionManager,
) : Progress.Host, Progress.Client {

    private val progressPub = MutableStateFlow<Progress.Data?>(Progress.Data())
    override val progress: Flow<Progress.Data?> = progressPub.throttleLatest(250)

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    private val matchesInternal = MutableSharedFlow<MatchEvent>(extraBufferCapacity = 1024)
    val matchEvents: Flow<MatchEvent> = matchesInternal

    suspend fun crawl(filters: Set<SystemCleanerFilter>): Collection<FilterContent> {
        log(TAG) { "crawl()" }
        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_searching)
        updateProgressSecondary(eu.darken.sdmse.common.R.string.general_progress_generating_searchpaths)
        updateProgressCount(Progress.Count.Indeterminate())

        val exclusions = exclusionManager.pathExclusions(SDMTool.Type.SYSTEMCLEANER)


        val currentAreas = areaManager.currentAreas()
        val targetAreas = filters
            .map { it.targetAreas() }
            .flatten()
            .toSet()
            .map { type -> currentAreas.filter { it.type == type } }
            .flatten()
            .toSet()
        log(TAG) { "Target areas wanted by filters: $targetAreas" }

        val sdcardSkips = mutableSetOf(
            segs("Android", "data"),
            segs("Android", "media"),
            segs("Android", "obb"),
        )

        val globalSkips = mutableSetOf<Segments>()
        if (targetAreas.all { it.path.segments != segs("", "data", "data") }) {
            globalSkips.add(segs("", "data", "data"))
            globalSkips.add(segs("", "data", "user", "0"))
        }
        globalSkips.add(segs("", "data", "media", "0"))
        log(TAG) { "Global skip segments: $globalSkips" }

        val sieveContents = mutableMapOf<FilterIdentifier, Set<SystemCleanerFilter.Match>>()

        gatewaySwitch.useRes {
            targetAreas
                .asFlow()
                .flowOn(dispatcherProvider.IO)
                .flatMapMerge(3) { area ->
                    val filter: suspend (APathLookup<*>) -> Boolean = when (area.type) {
                        DataArea.Type.SDCARD -> filter@{ toCheck: APathLookup<*> ->
                            if (sdcardSkips.any { toCheck.segments.endsWith(it) }) return@filter false
                            exclusions.none { it.match(toCheck) }
                        }

                        else -> filter@{ toCheck: APathLookup<*> ->
                            if (globalSkips.any { toCheck.segments.startsWith(it) }) {
                                log(TAG, WARN) { "Skipping: $toCheck" }
                                return@filter false
                            }
                            exclusions.none { it.match(toCheck) }
                        }
                    }
                    area.path.walk(
                        gatewaySwitch,
                        options = APathGateway.WalkOptions(
                            onFilter = filter
                        )
                    ).map { area to it }
                }
                .buffer(1024)
                .collect { (area, item) ->
                    if (Bugs.isTrace) log(TAG, VERBOSE) { "Trying to match $item" }
                    updateProgressSecondary(item.path)
                    val matched: Pair<SystemCleanerFilter, SystemCleanerFilter.Match>? = filters
                        .filter { it.targetAreas().contains(area.type) }
                        .firstNotNullOfOrNull { filter ->
                            val match = try {
                                filter.match(item)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: IOException) {
                                log(TAG, WARN) { "IO error while matching ($filter): ${e.asLog()}" }
                                null
                            } catch (e: Exception) {
                                log(TAG, ERROR) { "Sieve failed ($filter): ${e.asLog()}" }
                                throw SystemCleanerFilterException(filter, e)
                            }
                            if (match != null) filter to match else null
                        }

                    if (matched != null) {
                        log(TAG, INFO) { "Filter match: $matched <- $item" }
                        sieveContents[matched.first.identifier] =
                            (sieveContents[matched.first.identifier] ?: emptySet()).plus(matched.second)
                        matchesInternal.emit(MatchEvent(matched.first, item))
                    }
                }
        }

        val firstPass = sieveContents.map { entry ->
            log(TAG, INFO) { "${entry.key} has ${entry.value.size} matches (first pass)." }
            val filter = filters.single { it.identifier == entry.key }
            FilterContent(
                identifier = entry.key,
                icon = filter.getIcon(),
                label = filter.getLabel(),
                description = filter.getDescription(),
                items = entry.value,
            )
        }

        // With nested exclusions applied

        return firstPass
            .map { it.copy(items = exclusions.excludeNestedLookups(it.items)) }
            .onEach { log(TAG, INFO) { "${it.identifier} has ${it.items.size} matches (second pass)." } }
            .filter { it.items.isNotEmpty() }
    }

    data class MatchEvent(
        val filter: SystemCleanerFilter,
        val match: APathLookup<*>,
    )

    companion object {
        private val TAG = logTag("SystemCleaner", "Crawler")
    }
}