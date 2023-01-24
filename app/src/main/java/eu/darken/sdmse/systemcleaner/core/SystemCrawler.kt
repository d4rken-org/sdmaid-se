package eu.darken.sdmse.systemcleaner.core

import dagger.Reusable
import eu.darken.sdmse.R
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.areas.currentAreas
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.*
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.updateProgressCount
import eu.darken.sdmse.common.progress.updateProgressPrimary
import eu.darken.sdmse.common.progress.updateProgressSecondary
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilter
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@Reusable
class SystemCrawler @Inject constructor(
    private val areaManager: DataAreaManager,
    private val gatewaySwitch: GatewaySwitch,
    private val filterFactories: Set<@JvmSuppressWildcards SystemCleanerFilter.Factory>,
    private val dispatcherProvider: DispatcherProvider,
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
        updateProgressPrimary(R.string.general_progress_searching)
        updateProgressSecondary(R.string.general_progress_generating_searchpaths)
        updateProgressCount(Progress.Count.Indeterminate())

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
                    val filter = if (area.type == DataArea.Type.SDCARD) {
                        filter@{ toCheck: APathLookup<*> ->
                            if (sdcardOverlaps.any { it.isAncestorOf(toCheck) }) return@filter false
                            true
                        }
                    } else {
                        filter@{ toCheck: APathLookup<*> ->
                            if (skipSegments.any { toCheck.segments.startsWith(it) }) {
                                log(TAG, WARN) { "Skipping: $toCheck" }
                                return@filter false
                            }
                            true
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
                            } catch (e: Exception) {
                                log(TAG, ERROR) { "Sieve failed ($it): ${e.asLog()}" }
                                false
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
                filterIdentifier = entry.key.filterIdentifier,
                items = entry.value,
            )
        }
    }

    companion object {
        private val TAG = logTag("SystemCleaner", "Crawler")
    }
}