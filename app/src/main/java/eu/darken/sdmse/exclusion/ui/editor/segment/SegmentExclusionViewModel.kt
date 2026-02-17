package eu.darken.sdmse.exclusion.ui.editor.segment

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.segs
import eu.darken.sdmse.common.files.toSegs
import eu.darken.sdmse.common.flow.DynamicStateFlow
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.current
import eu.darken.sdmse.exclusion.core.remove
import eu.darken.sdmse.exclusion.core.save
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.ExclusionId
import eu.darken.sdmse.exclusion.core.types.SegmentExclusion
import javax.inject.Inject


@HiltViewModel
class SegmentExclusionViewModel @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val exclusionManager: ExclusionManager,
    private val pkgRepo: PkgRepo,
) : ViewModel3(dispatcherProvider) {

    private val navArgs by handle.navArgs<SegmentExclusionFragmentArgs>()
    private val initialOptions: SegmentExclusionEditorOptions? = navArgs.initial
    private val identifier: ExclusionId? = navArgs.exclusionId

    val events = SingleLiveEvent<SegmentExclusionEvents>()

    private val currentState = DynamicStateFlow(TAG, vmScope) {
        val origExclusion = exclusionManager.current().singleOrNull { it.id == identifier } as SegmentExclusion?

        if (origExclusion == null && initialOptions == null) {
            throw IllegalArgumentException("Neither existing exclusion nor init options were available")
        }

        val newExcl = origExclusion ?: INITIAL

        State(
            original = origExclusion,
            current = newExcl,
        )
    }

    val state = currentState.flow.asLiveData2()

    fun toggleTag(tag: Exclusion.Tag) = launch {
        log(TAG) { "toggleTag($tag)" }
        currentState.updateBlocking {
            val old = this.current
            val allTags = Exclusion.Tag.values().toSet()
            val allTools = allTags.minus(Exclusion.Tag.GENERAL)

            var newTags = when {
                old.tags.contains(tag) -> old.tags.minus(tag)
                else -> old.tags.minus(Exclusion.Tag.GENERAL).plus(tag)
            }

            if (newTags.contains(Exclusion.Tag.GENERAL) || newTags == allTags || newTags == allTools) {
                newTags = setOf(Exclusion.Tag.GENERAL)
            }

            val newExclusion = old.copy(tags = newTags)
            copy(current = newExclusion)
        }
    }

    fun save() = launch {
        log(TAG) { "save()" }
        val snap = currentState.value()
        snap.original?.id?.let {
            log(TAG) { "save(): Segments (ID) changed, removing previous: $it" }
            exclusionManager.remove(it)
        }
        exclusionManager.save(snap.current)
        popNavStack()
    }

    fun remove(confirmed: Boolean = false) = launch {
        val snap = currentState.value()
        log(TAG) { "remove() state=$snap" }
        if (!snap.canRemove) return@launch

        if (!confirmed) {
            events.postValue(SegmentExclusionEvents.RemoveConfirmation(snap.current))
        } else {
            exclusionManager.remove(snap.current.id)
            popNavStack()
        }
    }

    fun cancel(confirmed: Boolean = false) = launch {
        log(TAG) { "cancel()" }
        val snap = currentState.value()
        if (snap.canSave && !confirmed) {
            events.postValue(SegmentExclusionEvents.UnsavedChangesConfirmation(snap.current))
        } else {
            popNavStack()
        }
    }

    fun updateSegments(raw: String) = launch {
        log(TAG, VERBOSE) { "updateSegments($raw)" }
        currentState.updateBlocking {
            val newCurrent = current.copy(segments = raw.toSegs())
            copy(current = newCurrent)
        }
    }

    fun toggleAllowPartial() = launch {
        log(TAG, VERBOSE) { "toggleAllowPartial()" }
        currentState.updateBlocking {
            val newCurrent = current.copy(allowPartial = !current.allowPartial)
            copy(current = newCurrent)
        }
    }

    fun toggleIgnoreCase() = launch {
        log(TAG, VERBOSE) { "toggleIgnoreCase()" }
        currentState.updateBlocking {
            val newCurrent = current.copy(ignoreCase = !current.ignoreCase)
            copy(current = newCurrent)
        }
    }

    data class State(
        val original: SegmentExclusion?,
        val current: SegmentExclusion,
    ) {
        val canRemove: Boolean = original != null
        val canSave: Boolean = original != current && current != INITIAL
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