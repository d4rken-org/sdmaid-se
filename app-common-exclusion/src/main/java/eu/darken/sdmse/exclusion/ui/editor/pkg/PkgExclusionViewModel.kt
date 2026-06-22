package eu.darken.sdmse.exclusion.ui.editor.pkg

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.SingleEventFlow
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.get
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.current
import eu.darken.sdmse.exclusion.core.remove
import eu.darken.sdmse.exclusion.core.save
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.ExclusionId
import eu.darken.sdmse.exclusion.core.types.PkgExclusion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

@HiltViewModel
class PkgExclusionViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val exclusionManager: ExclusionManager,
    private val pkgRepo: PkgRepo,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    val events = SingleEventFlow<Event>()

    private data class RouteArgs(val exclusionId: String?, val initial: PkgExclusionEditorOptions?)

    private val routeFlow = MutableStateFlow<RouteArgs?>(null)
    private val currentFlow = MutableStateFlow<PkgExclusion?>(null)
    private val originalFlow = MutableStateFlow<PkgExclusion?>(null)
    private val pkgFlow = MutableStateFlow<Pkg?>(null)
    private val fatalFlow = MutableStateFlow(false)

    fun setArgs(exclusionId: String?, initial: PkgExclusionEditorOptions?) {
        if (routeFlow.value != null) return
        if (exclusionId == null && initial == null) {
            log(TAG, WARN) { "setArgs: neither exclusionId nor initial options were provided" }
            fatalFlow.value = true
            return
        }
        log(TAG, INFO) { "setArgs($exclusionId, $initial)" }
        routeFlow.value = RouteArgs(exclusionId, initial)
        launch { hydrate() }
    }

    private suspend fun hydrate() {
        val args = routeFlow.value ?: return
        val identifier: ExclusionId = args.exclusionId ?: PkgExclusion.createId(args.initial!!.targetPkgId)
        val orig = exclusionManager.current().singleOrNull { it.id == identifier } as PkgExclusion?

        if (orig == null && args.initial == null) {
            log(TAG, WARN) { "hydrate: exclusion $identifier not found and no initial provided" }
            fatalFlow.value = true
            return
        }

        val initial = orig ?: PkgExclusion(
            pkgId = args.initial!!.targetPkgId,
            tags = setOf(Exclusion.Tag.GENERAL),
        )
        originalFlow.value = orig
        currentFlow.value = initial
        pkgFlow.value = pkgRepo.get(initial.pkgId).firstOrNull()
    }

    val state: StateFlow<State> = fatalFlow
        .flatMapLatest { fatal ->
            if (fatal) flow { emit(State.NotFound) }
            else combine(currentFlow, originalFlow, pkgFlow) { current, original, pkg ->
                if (current == null) State.Loading
                else State.Ready(
                    current = current,
                    pkg = pkg,
                    canSave = current != original,
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

    fun save() = launch {
        val current = currentFlow.value ?: return@launch
        log(TAG) { "save()" }
        exclusionManager.save(current)
        navUp()
    }

    fun remove(confirmed: Boolean = false) = launch {
        val snap = (state.value as? State.Ready) ?: return@launch
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
            val current: PkgExclusion,
            val pkg: Pkg?,
            val canSave: Boolean,
            val canRemove: Boolean,
        ) : State
    }

    sealed interface Event {
        data object RemoveConfirmation : Event
        data object UnsavedChangesConfirmation : Event
    }

    companion object {
        private val TAG = logTag("Exclusion", "Editor", "Pkg", "ViewModel")
    }
}
