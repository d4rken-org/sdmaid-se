package eu.darken.sdmse.systemcleaner.ui.customfilter.editor

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.flow.DynamicStateFlow
import eu.darken.sdmse.common.flow.combine
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.flow.withPrevious
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.sieve.NameCriterium
import eu.darken.sdmse.common.sieve.SegmentCriterium
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import eu.darken.sdmse.systemcleaner.core.SystemCrawler
import eu.darken.sdmse.systemcleaner.core.filter.FilterIdentifier
import eu.darken.sdmse.systemcleaner.core.filter.custom.CustomFilter
import eu.darken.sdmse.systemcleaner.core.filter.custom.CustomFilterConfig
import eu.darken.sdmse.systemcleaner.core.filter.custom.CustomFilterRepo
import eu.darken.sdmse.systemcleaner.core.filter.custom.currentConfigs
import eu.darken.sdmse.systemcleaner.core.filter.custom.toggleCustomFilter
import eu.darken.sdmse.systemcleaner.ui.customfilter.editor.live.LiveSearchListRow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
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
) : ViewModel3(dispatcherProvider) {

    private val navArgs by handle.navArgs<CustomFilterEditorFragmentArgs>()
    private val initialOptions: CustomFilterEditorOptions? = navArgs.initial
    private val identifier: FilterIdentifier = navArgs.identifier ?: filterRepo.generateIdentifier()

    val events = SingleLiveEvent<CustomFilterEditorEvents>()

    private val currentState = DynamicStateFlow(TAG, vmScope) {
        val originalConfig = filterRepo.currentConfigs().singleOrNull { it.identifier == identifier }

        if (originalConfig == null && initialOptions == null) {
            throw IllegalArgumentException("Neither existing config nor init options were available")
        }

        val newConfig = originalConfig ?: CustomFilterConfig(
            identifier = identifier,
            label = initialOptions!!.label ?: "",
            areas = initialOptions.areas,
            pathCriteria = initialOptions.pathCriteria,
            nameCriteria = initialOptions.nameCriteria,
        )

        State(
            original = originalConfig,
            current = newConfig,
        )
    }

    val state = currentState.flow.asLiveData2()

    init {
        dataAreaManager.state
            .onEach { areaState ->
                currentState.updateBlocking {
                    copy(availableAreas = areaState.areas.map { it.type }.toSet())
                }
            }
            .launchInViewModel()
    }

    fun save() = launch {
        log(TAG) { "save()" }
        val toSave = currentState.value().current.copy(modifiedAt = Instant.now())
        filterRepo.save(setOf(toSave))
        if (initialOptions?.saveAsEnabled == true) {
            settings.toggleCustomFilter(toSave.identifier, true)
        }
        popNavStack()
    }

    fun remove(confirmed: Boolean = false) = launch {
        val snap = currentState.value()
        log(TAG) { "remove() state=$snap" }
        if (!snap.canRemove) return@launch

        if (!confirmed) {
            events.postValue(CustomFilterEditorEvents.RemoveConfirmation(snap.current))
        } else {
            filterRepo.remove(setOf(snap.current.identifier))
            popNavStack()
        }
    }

    fun cancel(confirmed: Boolean = false) = launch {
        log(TAG) { "cancel()" }
        val snap = currentState.value()
        if ((snap.canSave || snap.hasUnchanged) && !confirmed) {
            events.postValue(CustomFilterEditorEvents.UnsavedChangesConfirmation(snap.current))
        } else {
            popNavStack()
        }
    }

    fun updateLabel(label: String) = launch {
        log(TAG) { "updateLabel($label)" }
        currentState.updateBlocking {
            copy(current = this.current.copy(label = label))
        }
    }

    fun addPath(criterium: SegmentCriterium) = launch {
        log(TAG) { "addPath($criterium)" }
        currentState.updateBlocking {
            val new = (current.pathCriteria ?: emptySet()).toMutableSet().apply { add(criterium) }
            copy(current = current.copy(pathCriteria = new))
        }
    }

    fun removePath(criterium: SegmentCriterium) = launch {
        log(TAG) { "removePath($criterium)" }
        currentState.updateBlocking {
            val new = (current.pathCriteria ?: emptySet()).toMutableSet().apply { remove(criterium) }
            copy(current = current.copy(pathCriteria = new.takeIf { it.isNotEmpty() }))
        }
    }

    fun addNameContains(criterium: NameCriterium) = launch {
        log(TAG) { "addNameContains($criterium)" }
        currentState.updateBlocking {
            val new = (current.nameCriteria ?: emptySet()) + criterium
            copy(current = current.copy(nameCriteria = new))
        }
    }

    fun removeNameContains(criterium: NameCriterium) = launch {
        log(TAG) { "removeNameContains($criterium)" }
        currentState.updateBlocking {
            val new = (current.nameCriteria ?: emptySet()) - criterium
            copy(current = current.copy(nameCriteria = new.takeIf { it.isNotEmpty() }))
        }
    }

    fun addExclusion(criterium: SegmentCriterium) = launch {
        log(TAG) { "addExclusion($criterium)" }
        currentState.updateBlocking {
            val new = (current.exclusionCriteria ?: emptySet()).toMutableSet().apply { add(criterium) }
            copy(current = current.copy(exclusionCriteria = new))
        }
    }

    fun removeExclusion(criterium: SegmentCriterium) = launch {
        log(TAG) { "removeExclusion($criterium)" }
        currentState.updateBlocking {
            val new = (current.exclusionCriteria ?: emptySet()).toMutableSet().apply { remove(criterium) }
            copy(current = current.copy(exclusionCriteria = new.takeIf { it.isNotEmpty() }))
        }
    }

    fun toggleArea(type: DataArea.Type, checked: Boolean) = launch {
        log(TAG) { "toggleArea($type,$checked)" }
        currentState.updateBlocking {
            val new = (current.areas ?: emptySet()).toMutableSet().apply {
                if (checked) add(type) else remove(type)
            }
            copy(current = current.copy(areas = new.takeIf { it.isNotEmpty() }))
        }
    }

    fun toggleFileType(type: FileType) = launch {
        log(TAG) { "toggleFileType($type)" }
        currentState.updateBlocking {
            val newFileTypes = if (current.fileTypes?.contains(type) == true) {
                current.fileTypes - type
            } else if (current.fileTypes?.contains(type) == false) {
                current.fileTypes + type
            } else {
                setOf(type)
            }
            copy(current = current.copy(fileTypes = newFileTypes.takeIf { it.isNotEmpty() }))
        }
    }

    fun updateSizeMinimum(size: Long?) = launch {
        log(TAG) { "updateSizeMinimum($size)" }
        currentState.updateBlocking {
            copy(current = current.copy(sizeMinimum = size))
        }
    }

    fun updateSizeMaximum(size: Long?) = launch {
        log(TAG) { "updateSizeMaximum($size)" }
        currentState.updateBlocking {
            copy(current = current.copy(sizeMaximum = size))
        }
    }

    fun updateAgeMinimum(age: Duration?) = launch {
        log(TAG) { "updateAgeMinimum($age)" }
        currentState.updateBlocking {
            copy(current = current.copy(ageMinimum = age))
        }
    }

    fun updateAgeMaximum(age: Duration?) = launch {
        log(TAG) { "updateAgeMaximum($age)" }
        currentState.updateBlocking {
            copy(current = current.copy(ageMaximum = age))
        }
    }

    data class State(
        val original: CustomFilterConfig?,
        val current: CustomFilterConfig,
        val availableAreas: Set<DataArea.Type>? = null,
    ) {
        val canRemove: Boolean = original != null
        val canSave: Boolean = original != current && !current.isUnderdefined && current.label.isNotEmpty()
        val hasUnchanged: Boolean = if (original != null) original != current else !current.isDefault

    }

    val liveSearch = currentState.flow
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
                    .map { LiveSearchListRow.Item(lookup = it.match) }
                    .scan(listOf<LiveSearchListRow.Item>()) { list, event -> list.plus(event) },
                crawler.progress,
                crawlerJobFlow,
            ) { matches, progress, isWorking ->
                LiveSearchState(matches, if (isWorking) progress else null)
            }
                .flowOn(dispatcherProvider.IO)
                .throttleLatest(200)
        }
        .onStart { emit(LiveSearchState(firstInit = true)) }
        .asLiveData2()

    data class LiveSearchState(
        val matches: List<LiveSearchListRow.Item> = emptyList(),
        val progress: Progress.Data? = null,
        val firstInit: Boolean = false,
    )

    companion object {
        private val TAG = logTag("SystemCleaner", "CustomFilter", "Editor", "ViewModel")
    }

}