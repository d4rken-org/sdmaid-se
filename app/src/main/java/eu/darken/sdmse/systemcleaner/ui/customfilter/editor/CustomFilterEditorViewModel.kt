package eu.darken.sdmse.systemcleaner.ui.customfilter.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.Segments
import eu.darken.sdmse.common.files.joinSegments
import eu.darken.sdmse.common.files.toSegs
import eu.darken.sdmse.common.flow.DynamicStateFlow
import eu.darken.sdmse.common.flow.combine
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.systemcleaner.core.SystemCrawler
import eu.darken.sdmse.systemcleaner.core.filter.FilterIdentifier
import eu.darken.sdmse.systemcleaner.core.filter.custom.CustomFilter
import eu.darken.sdmse.systemcleaner.core.filter.custom.CustomFilterConfig
import eu.darken.sdmse.systemcleaner.core.filter.custom.CustomFilterRepo
import eu.darken.sdmse.systemcleaner.core.filter.custom.currentConfigs
import eu.darken.sdmse.systemcleaner.ui.customfilter.editor.live.LiveSearchListRow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.scan
import java.time.Instant
import javax.inject.Inject


@HiltViewModel
class CustomFilterEditorViewModel @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val filterRepo: CustomFilterRepo,
    private val dataAreaManager: DataAreaManager,
    private val crawler: SystemCrawler,
    private val filterFactory: CustomFilter.Factory,
) : ViewModel3(dispatcherProvider) {

    private val navArgs by handle.navArgs<CustomFilterEditorFragmentArgs>()
    private val initialOptions: CustomFilterEditorOptions? = navArgs.initial
    private val identifier: FilterIdentifier = navArgs.identifier ?: filterRepo.generateIdentifier()

    val events = SingleLiveEvent<CustomFilterEditorEvents>()

    private val currentState = DynamicStateFlow(TAG, viewModelScope) {
        val originalConfig = filterRepo.currentConfigs().singleOrNull { it.identifier == identifier }

        if (originalConfig == null && initialOptions == null) {
            throw IllegalArgumentException("Neither existing config nor init options were available")
        }

        val newConfig = originalConfig ?: CustomFilterConfig(
            identifier = identifier,
            label = "",
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

    fun pathToTag(segments: Segments): TaggedInputView.Tag = TaggedInputView.Tag(
        value = segments.joinSegments()
    )

    private fun TaggedInputView.Tag.toPath(): Segments = value.toSegs()

    fun nameToTag(name: String): TaggedInputView.Tag = TaggedInputView.Tag(
        value = name
    )

    private fun TaggedInputView.Tag.toName(): String = value

    fun addPath(tag: TaggedInputView.Tag) = launch {
        val path: Segments = tag.toPath()
        log(TAG) { "addPath($tag) -> $path" }
        currentState.updateBlocking {
            val new = (current.pathContains ?: emptySet()).toMutableSet().apply { add(path) }
            copy(current = current.copy(pathContains = new))
        }
    }

    fun removePath(tag: TaggedInputView.Tag) = launch {
        val path: Segments = tag.toPath()
        log(TAG) { "removePath($tag) -> $path" }
        currentState.updateBlocking {
            val new = (current.pathContains ?: emptySet()).toMutableSet().apply { remove(path) }
            copy(current = current.copy(pathContains = new.takeIf { it.isNotEmpty() }))
        }
    }

    fun addNameContains(tag: TaggedInputView.Tag) = launch {
        val name = tag.toName()
        log(TAG) { "addNameContains($tag) -> $name" }
        currentState.updateBlocking {
            val new = (current.nameContains ?: emptySet()) + name
            copy(current = current.copy(nameContains = new))
        }
    }

    fun removeNameContains(tag: TaggedInputView.Tag) = launch {
        val name = tag.toName()
        log(TAG) { "removeNameContains($tag) -> $name" }
        currentState.updateBlocking {
            val new = (current.nameContains ?: emptySet()) - name
            copy(current = current.copy(nameContains = new.takeIf { it.isNotEmpty() }))
        }
    }

    fun addNameEndsWith(tag: TaggedInputView.Tag) = launch {
        val ending = tag.toName()
        log(TAG) { "addNameEndsWith($tag) -> $ending" }
        currentState.updateBlocking {
            val new = (current.nameEndsWith ?: emptySet()) + ending
            copy(current = current.copy(nameEndsWith = new))
        }
    }

    fun removeNameEndsWith(tag: TaggedInputView.Tag) = launch {
        val ending = tag.toName()
        log(TAG) { "removeNameEndsWith($tag) -> $ending" }
        currentState.updateBlocking {
            val new = (current.nameEndsWith ?: emptySet()) - ending
            copy(current = current.copy(nameEndsWith = new.takeIf { it.isNotEmpty() }))
        }
    }

    fun addExclusion(tag: TaggedInputView.Tag) = launch {
        val path: Segments = tag.toPath()
        log(TAG) { "addExclusion($tag) -> $path" }
        currentState.updateBlocking {
            val new = (current.exclusion ?: emptySet()).toMutableSet().apply { add(path) }
            copy(current = current.copy(exclusion = new))
        }
    }

    fun removeExclusion(tag: TaggedInputView.Tag) = launch {
        val path: Segments = tag.toPath()
        log(TAG) { "removeExclusion($tag) -> $path" }
        currentState.updateBlocking {
            val new = (current.exclusion ?: emptySet()).toMutableSet().apply { remove(path) }
            copy(current = current.copy(exclusion = new.takeIf { it.isNotEmpty() }))
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

    data class State(
        val original: CustomFilterConfig?,
        val current: CustomFilterConfig,
        val availableAreas: Set<DataArea.Type>? = null,
    ) {
        val canRemove: Boolean = original != null
        val canSave: Boolean = original != current && !current.isUnderdefined && current.label.isNotEmpty()
        val hasUnchanged: Boolean = original != current
    }

    val liveSearch = currentState.flow
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
            }.throttleLatest(200)
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