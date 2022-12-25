package eu.darken.sdmse.systemcleaner.core

import dagger.Reusable
import eu.darken.sdmse.R
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.areas.currentAreas
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.APathLookup
import eu.darken.sdmse.common.files.core.GatewaySwitch
import eu.darken.sdmse.common.files.core.walk
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.updateProgressCount
import eu.darken.sdmse.common.progress.updateProgressPrimary
import eu.darken.sdmse.common.progress.updateProgressSecondary
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilter
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@Reusable
class SystemCrawler @Inject constructor(
    private val areaManager: DataAreaManager,
    private val gatewaySwitch: GatewaySwitch,
    private val filterFactories: Set<@JvmSuppressWildcards SystemCleanerFilter.Factory>,
    private val dispatcherProvider: DispatcherProvider,
    private val rootManager: RootManager,
) : Progress.Host, Progress.Client {

    private val progressPub = MutableStateFlow<Progress.Data?>(null)
    override val progress: Flow<Progress.Data?> = progressPub.throttleLatest(250)

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    suspend fun crawl(): Collection<SieveContent> {
        updateProgressPrimary(R.string.general_progress_searching)
        updateProgressSecondary(R.string.general_progress_generating_searchpaths)
        updateProgressCount(Progress.Count.Indeterminate())

        val filters = filterFactories
            .filter { it.isEnabled() }
            .map { it.create() }
            .onEach { it.initialize() }

        val currentAreas = areaManager.currentAreas()
        val targetAreas = filters
            .map { it.targetAreas() }
            .flatten()
            .toSet()
            .map { type -> currentAreas.filter { it.type == type } }
            .flatten()
            .toSet()

        val sieveContents = mutableMapOf<SystemCleanerFilter, Set<APathLookup<*>>>()

        gatewaySwitch.useRes {
            targetAreas
                .asFlow()
                .flowOn(dispatcherProvider.IO)
                .flatMapMerge(4) { area ->
                    // TODO prevent overlap between /storage/emulated/0 and /data/media/0?
                    area.path.walk(gatewaySwitch)
                }
                .buffer(256)
                .collectLatest { item ->
                    updateProgressSecondary(item.path)
                    val matched = filters.firstOrNull { it.sieve(item) }
                    if (matched != null) {
                        log(TAG) { "$matched matched $item" }
                        sieveContents[matched] = (sieveContents[matched] ?: emptySet()).plus(item)
                    }
                }
        }

        return sieveContents.map { entry ->
            SieveContent(
                items = entry.value
            )
        }
    }

    companion object {
        private val TAG = logTag("SystemCleaner", "Crawler")
    }
}