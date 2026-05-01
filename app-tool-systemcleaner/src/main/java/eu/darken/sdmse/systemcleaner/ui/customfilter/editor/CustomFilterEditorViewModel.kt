package eu.darken.sdmse.systemcleaner.ui.customfilter.editor

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.filter.CustomFilterEditorOptions
import eu.darken.sdmse.common.filter.CustomFilterEditorRoute
import eu.darken.sdmse.common.flow.DynamicStateFlow
import eu.darken.sdmse.common.flow.SingleEventFlow
import eu.darken.sdmse.common.flow.combine
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.flow.withPrevious
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.sieve.NameCriterium
import eu.darken.sdmse.common.sieve.SegmentCriterium
import eu.darken.sdmse.common.sieve.SieveCriterium
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import eu.darken.sdmse.systemcleaner.core.SystemCrawler
import eu.darken.sdmse.systemcleaner.core.filter.FilterIdentifier
import eu.darken.sdmse.systemcleaner.core.filter.custom.CustomFilter
import eu.darken.sdmse.systemcleaner.core.filter.custom.CustomFilterConfig
import eu.darken.sdmse.systemcleaner.core.filter.custom.CustomFilterRepo
import eu.darken.sdmse.systemcleaner.core.filter.custom.currentConfigs
import eu.darken.sdmse.systemcleaner.core.filter.custom.toggleCustomFilter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.scan
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class CustomFilterEditorViewModel @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val filterRepo: CustomFilterRepo,
    dataAreaManager: DataAreaManager,
    private val crawler: SystemCrawler,
    private val filterFactory: CustomFilter.Factory,
    private val settings: SystemCleanerSettings,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    private val route = CustomFilterEditorRoute.from(handle)
    private val initialOptions: CustomFilterEditorOptions? = route.initial
    private val identifier: FilterIdentifier = route.identifier ?: filterRepo.generateIdentifier()

    val events = SingleEventFlow<Event>()

    private val currentState = DynamicStateFlow(TAG, vmScope) {
        val originalConfig = filterRepo.currentConfigs().singleOrNull { it.identifier == identifier }

        if (originalConfig == null && initialOptions == null) {
            // Edit-by-identifier route landed on a config that no longer exists. Surface the
            // error and unwind instead of crashing or rendering a phantom blank filter.
            errorEvents.emit(IllegalStateException("Custom filter $identifier does not exist"))
            navUp()
            // Return a transient placeholder; the navUp will tear the screen down before this
            // emission has any visible effect.
            return@DynamicStateFlow State(
                original = null,
                current = CustomFilterConfig(identifier = identifier, label = ""),
            )
        }

        val seedConfig = originalConfig ?: CustomFilterConfig(
            identifier = identifier,
            label = initialOptions!!.label ?: "",
            areas = initialOptions.areas,
            pathCriteria = initialOptions.pathCriteria,
            nameCriteria = initialOptions.nameCriteria,
        )

        State(
            original = originalConfig,
            current = seedConfig,
        )
    }

    val state: StateFlow<State?> = currentState.flow.safeStateIn(initialValue = null) { null }

    init {
        dataAreaManager.state
            .onEach { areaState ->
                currentState.updateBlocking {
                    copy(availableAreas = areaState.areas.map { it.type }.toSet())
                }
            }
            .launchIn(vmScope)
    }

    fun save() = launch {
        log(TAG) { "save()" }
        val toSave = currentState.value().current.copy(modifiedAt = Instant.now())
        filterRepo.save(setOf(toSave))
        if (initialOptions?.saveAsEnabled == true) {
            settings.toggleCustomFilter(toSave.identifier, true)
        }
        navUp()
    }

    fun remove(confirmed: Boolean = false) = launch {
        val snap = currentState.value()
        log(TAG) { "remove() state=$snap" }
        if (!snap.canRemove) return@launch

        if (!confirmed) {
            events.tryEmit(Event.RemoveConfirmation(snap.current))
        } else {
            filterRepo.remove(setOf(snap.current.identifier))
            navUp()
        }
    }

    fun cancel(confirmed: Boolean = false) = launch {
        log(TAG) { "cancel()" }
        val snap = currentState.value()
        if ((snap.canSave || snap.hasUnchanged) && !confirmed) {
            events.tryEmit(Event.UnsavedChangesConfirmation(snap.current))
        } else {
            navUp()
        }
    }

    fun updateLabel(label: String) = launch {
        currentState.updateBlocking {
            copy(current = current.copy(label = label))
        }
    }

    fun addPath(criterium: SegmentCriterium) = launch {
        currentState.updateBlocking {
            val new = (current.pathCriteria ?: emptySet()).toMutableSet().apply { add(criterium) }
            copy(current = current.copy(pathCriteria = new))
        }
    }

    fun removePath(criterium: SegmentCriterium) = launch {
        currentState.updateBlocking {
            val new = (current.pathCriteria ?: emptySet()).toMutableSet().apply { remove(criterium) }
            copy(current = current.copy(pathCriteria = new.takeIf { it.isNotEmpty() }))
        }
    }

    fun swapPath(old: SieveCriterium, new: SieveCriterium) = launch {
        currentState.updateBlocking {
            val swapped = swapPreservingOrder(current.pathCriteria, old, new)
                .filterIsInstance<SegmentCriterium>()
                .toCollection(LinkedHashSet())
            copy(current = current.copy(pathCriteria = swapped.takeIf { it.isNotEmpty() }))
        }
    }

    fun addNameContains(criterium: NameCriterium) = launch {
        currentState.updateBlocking {
            val new = (current.nameCriteria ?: emptySet()) + criterium
            copy(current = current.copy(nameCriteria = new))
        }
    }

    fun removeNameContains(criterium: NameCriterium) = launch {
        currentState.updateBlocking {
            val new = (current.nameCriteria ?: emptySet()) - criterium
            copy(current = current.copy(nameCriteria = new.takeIf { it.isNotEmpty() }))
        }
    }

    fun swapNameContains(old: SieveCriterium, new: SieveCriterium) = launch {
        currentState.updateBlocking {
            val swapped = swapPreservingOrder(current.nameCriteria, old, new)
                .filterIsInstance<NameCriterium>()
                .toCollection(LinkedHashSet())
            copy(current = current.copy(nameCriteria = swapped.takeIf { it.isNotEmpty() }))
        }
    }

    fun addExclusion(criterium: SegmentCriterium) = launch {
        currentState.updateBlocking {
            val new = (current.exclusionCriteria ?: emptySet()).toMutableSet().apply { add(criterium) }
            copy(current = current.copy(exclusionCriteria = new))
        }
    }

    fun removeExclusion(criterium: SegmentCriterium) = launch {
        currentState.updateBlocking {
            val new = (current.exclusionCriteria ?: emptySet()).toMutableSet().apply { remove(criterium) }
            copy(current = current.copy(exclusionCriteria = new.takeIf { it.isNotEmpty() }))
        }
    }

    fun swapExclusion(old: SieveCriterium, new: SieveCriterium) = launch {
        currentState.updateBlocking {
            val swapped = swapPreservingOrder(current.exclusionCriteria, old, new)
                .filterIsInstance<SegmentCriterium>()
                .toCollection(LinkedHashSet())
            copy(current = current.copy(exclusionCriteria = swapped.takeIf { it.isNotEmpty() }))
        }
    }

    fun toggleArea(type: DataArea.Type, checked: Boolean) = launch {
        currentState.updateBlocking {
            val new = (current.areas ?: emptySet()).toMutableSet().apply {
                if (checked) add(type) else remove(type)
            }
            copy(current = current.copy(areas = new.takeIf { it.isNotEmpty() }))
        }
    }

    fun toggleFileType(type: FileType) = launch {
        currentState.updateBlocking {
            val currentTypes = current.fileTypes
            val newFileTypes = if (currentTypes?.contains(type) == true) {
                currentTypes - type
            } else if (currentTypes?.contains(type) == false) {
                currentTypes + type
            } else {
                setOf(type)
            }
            copy(current = current.copy(fileTypes = newFileTypes.takeIf { it.isNotEmpty() }))
        }
    }

    fun updateSizeMinimum(size: Long?) = launch {
        currentState.updateBlocking { copy(current = current.copy(sizeMinimum = size)) }
    }

    fun updateSizeMaximum(size: Long?) = launch {
        currentState.updateBlocking { copy(current = current.copy(sizeMaximum = size)) }
    }

    fun updateAgeMinimum(age: Duration?) = launch {
        currentState.updateBlocking { copy(current = current.copy(ageMinimum = age)) }
    }

    fun updateAgeMaximum(age: Duration?) = launch {
        currentState.updateBlocking { copy(current = current.copy(ageMaximum = age)) }
    }

    val liveSearch: StateFlow<LiveSearchState> = currentState.flow
        .withPrevious()
        .filter { (old, new) ->
            if (old == null) return@filter true
            // Don't restart live search if just the label changes
            val old2 = old.copy(current = old.current.copy(label = ""))
            val new2 = new.copy(current = new.current.copy(label = ""))
            old2 != new2
        }
        .map { it.second }
        .flatMapLatest { state ->
            if (state.current.isUnderdefined) {
                log(TAG) { "Live search: Skipping due to under defined config" }
                return@flatMapLatest flowOf(LiveSearchState())
            }
            val config = state.current

            val crawlerJobFlow = callbackFlow {
                log(TAG) { "Crawler is starting for $config" }

                val filter = filterFactory.create(config).apply { initialize() }
                send(true)
                crawler.crawl(setOf(filter))
                send(false)

                awaitClose { log(TAG) { "Crawler finished search" } }
            }

            combine(
                crawler.matchEvents
                    .map { LiveSearchMatch(lookup = it.match) }
                    .scan(listOf<LiveSearchMatch>()) { list, event -> list.plus(event) },
                crawler.progress,
                crawlerJobFlow,
            ) { matches, progress, isWorking ->
                LiveSearchState(matches, if (isWorking) progress else null)
            }
                .flowOn(dispatcherProvider.IO)
                .throttleLatest(200)
        }
        .onStart { emit(LiveSearchState(firstInit = true)) }
        .safeStateIn(initialValue = LiveSearchState(firstInit = true)) { LiveSearchState(firstInit = true) }

    data class State(
        val original: CustomFilterConfig?,
        val current: CustomFilterConfig,
        val availableAreas: Set<DataArea.Type>? = null,
    ) {
        val canRemove: Boolean = original != null
        val canSave: Boolean = original != current && !current.isUnderdefined && current.label.isNotEmpty()
        val hasUnchanged: Boolean = if (original != null) original != current else !current.isDefault
    }

    data class LiveSearchState(
        val matches: List<LiveSearchMatch> = emptyList(),
        val progress: Progress.Data? = null,
        val firstInit: Boolean = false,
    )

    sealed interface Event {
        data class RemoveConfirmation(val config: CustomFilterConfig) : Event
        data class UnsavedChangesConfirmation(val config: CustomFilterConfig) : Event
    }

    companion object {
        private val TAG = logTag("SystemCleaner", "CustomFilter", "Editor", "ViewModel")
    }
}
