package eu.darken.sdmse.exclusion.ui.editor.segment

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.segs
import eu.darken.sdmse.common.files.toSegs
import eu.darken.sdmse.common.flow.SingleEventFlow
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.current
import eu.darken.sdmse.exclusion.core.remove
import eu.darken.sdmse.exclusion.core.save
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.ExclusionId
import eu.darken.sdmse.exclusion.core.types.SegmentExclusion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

@HiltViewModel
class SegmentExclusionViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val exclusionManager: ExclusionManager,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    val events = SingleEventFlow<Event>()

    private data class RouteArgs(val exclusionId: String?, val initial: SegmentExclusionEditorOptions?)

    private val routeFlow = MutableStateFlow<RouteArgs?>(null)
    private val currentFlow = MutableStateFlow<SegmentExclusion?>(null)
    private val originalFlow = MutableStateFlow<SegmentExclusion?>(null)
    private val fatalFlow = MutableStateFlow(false)

    fun setArgs(exclusionId: String?, initial: SegmentExclusionEditorOptions?) {
        if (routeFlow.value != null) return
        log(TAG, INFO) { "setArgs($exclusionId, $initial)" }
        routeFlow.value = RouteArgs(exclusionId, initial)
        launch { hydrate() }
    }

    private suspend fun hydrate() {
        val args = routeFlow.value ?: return
        val identifier: ExclusionId? = args.exclusionId
        val orig = identifier?.let {
            exclusionManager.current().singleOrNull { excl -> excl.id == it } as SegmentExclusion?
        }

        if (orig == null && args.initial == null && identifier != null) {
            log(TAG, WARN) { "hydrate: exclusion $identifier not found and no initial provided" }
            fatalFlow.value = true
            return
        }

        val initialExcl = orig ?: SegmentExclusion(
            segments = args.initial?.targetSegments ?: segs(""),
            allowPartial = true,
            ignoreCase = true,
            tags = setOf(Exclusion.Tag.GENERAL),
        )
        originalFlow.value = orig
        currentFlow.value = initialExcl
    }

    val state: StateFlow<State> = fatalFlow
        .flatMapLatest { fatal ->
            if (fatal) flow { emit(State.NotFound) }
            else combine(currentFlow, originalFlow) { current, original ->
                if (current == null) State.Loading
                else State.Ready(
                    current = current,
                    canSave = current != original && current != INITIAL,
                    canRemove = original != null,
                )
            }
        }
        .safeStateIn(
            initialValue = State.Loading,
            onError = { State.NotFound },
        )

    fun toggleTag(tag: Exclusion.Tag) = launch {
        val old = currentFlow.value ?: return@launch
        log(TAG) { "toggleTag($tag)" }
        val allTags = Exclusion.Tag.values().toSet()
        val allTools = allTags.minus(Exclusion.Tag.GENERAL)

        var newTags = when {
            old.tags.contains(tag) -> old.tags.minus(tag)
            else -> old.tags.minus(Exclusion.Tag.GENERAL).plus(tag)
        }

        if (newTags.contains(Exclusion.Tag.GENERAL) || newTags == allTags || newTags == allTools) {
            newTags = setOf(Exclusion.Tag.GENERAL)
        }
        currentFlow.value = old.copy(tags = newTags)
    }

    fun updateSegments(raw: String) = launch {
        val old = currentFlow.value ?: return@launch
        log(TAG, VERBOSE) { "updateSegments($raw)" }
        currentFlow.value = old.copy(segments = raw.toSegs())
    }

    fun toggleAllowPartial() = launch {
        val old = currentFlow.value ?: return@launch
        log(TAG, VERBOSE) { "toggleAllowPartial()" }
        currentFlow.value = old.copy(allowPartial = !old.allowPartial)
    }

    fun toggleIgnoreCase() = launch {
        val old = currentFlow.value ?: return@launch
        log(TAG, VERBOSE) { "toggleIgnoreCase()" }
        currentFlow.value = old.copy(ignoreCase = !old.ignoreCase)
    }

    fun save() = launch {
        val snap = state.value as? State.Ready ?: return@launch
        log(TAG) { "save()" }
        originalFlow.value?.id?.let {
            log(TAG) { "save(): Segments (ID) changed, removing previous: $it" }
            exclusionManager.remove(it)
        }
        exclusionManager.save(snap.current)
        navUp()
    }

    fun remove(confirmed: Boolean = false) = launch {
        val snap = state.value as? State.Ready ?: return@launch
        log(TAG) { "remove() confirmed=$confirmed" }
        if (!snap.canRemove) return@launch

        if (!confirmed) {
            events.emit(Event.RemoveConfirmation)
        } else {
            exclusionManager.remove(snap.current.id)
            navUp()
        }
    }

    fun cancel(confirmed: Boolean = false) = launch {
        log(TAG) { "cancel() confirmed=$confirmed" }
        val snap = state.value as? State.Ready
        if (snap != null && snap.canSave && !confirmed) {
            events.emit(Event.UnsavedChangesConfirmation)
        } else {
            navUp()
        }
    }

    sealed interface State {
        data object Loading : State
        data object NotFound : State
        data class Ready(
            val current: SegmentExclusion,
            val canSave: Boolean,
            val canRemove: Boolean,
        ) : State
    }

    sealed interface Event {
        data object RemoveConfirmation : Event
        data object UnsavedChangesConfirmation : Event
    }

    companion object {
        private val TAG = logTag("Exclusion", "Editor", "Segment", "ViewModel")
        private val INITIAL = SegmentExclusion(
            segments = segs(""),
            allowPartial = true,
            ignoreCase = true,
            tags = setOf(Exclusion.Tag.GENERAL),
        )
    }
}
