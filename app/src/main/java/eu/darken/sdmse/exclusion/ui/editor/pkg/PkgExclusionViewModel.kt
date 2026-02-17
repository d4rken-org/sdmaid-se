package eu.darken.sdmse.exclusion.ui.editor.pkg

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.DynamicStateFlow
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.get
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.current
import eu.darken.sdmse.exclusion.core.remove
import eu.darken.sdmse.exclusion.core.save
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.ExclusionId
import eu.darken.sdmse.exclusion.core.types.PkgExclusion
import javax.inject.Inject


@HiltViewModel
class PkgExclusionViewModel @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val exclusionManager: ExclusionManager,
    private val pkgRepo: PkgRepo,
) : ViewModel3(dispatcherProvider) {

    private val navArgs by handle.navArgs<PkgExclusionFragmentArgs>()
    private val initialOptions: PkgExclusionEditorOptions? = navArgs.initial
    private val identifier: ExclusionId = navArgs.exclusionId ?: PkgExclusion.createId(initialOptions!!.targetPkgId)

    val events = SingleLiveEvent<PkgExclusionEvents>()

    private val currentState = DynamicStateFlow(TAG, vmScope) {
        val origExclusion = exclusionManager.current().singleOrNull { it.id == identifier } as PkgExclusion?

        if (origExclusion == null && initialOptions == null) {
            throw IllegalArgumentException("Neither existing exclusion nor init options were available")
        }

        val newExcl = origExclusion ?: PkgExclusion(
            pkgId = initialOptions!!.targetPkgId,
            tags = setOf(Exclusion.Tag.GENERAL),
        )

        State(
            original = origExclusion,
            current = newExcl,
            pkg = pkgRepo.get(newExcl.pkgId).firstOrNull(),
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
        exclusionManager.save(currentState.value().current)
        popNavStack()
    }

    fun remove(confirmed: Boolean = false) = launch {
        val snap = currentState.value()
        log(TAG) { "remove() state=$snap" }
        if (!snap.canRemove) return@launch

        if (!confirmed) {
            events.postValue(PkgExclusionEvents.RemoveConfirmation(snap.current))
        } else {
            exclusionManager.remove(snap.current.id)
            popNavStack()
        }
    }

    fun cancel(confirmed: Boolean = false) = launch {
        log(TAG) { "cancel()" }
        val snap = currentState.value()
        if (snap.canSave && !confirmed) {
            events.postValue(PkgExclusionEvents.UnsavedChangesConfirmation(snap.current))
        } else {
            popNavStack()
        }
    }

    data class State(
        val original: PkgExclusion?,
        val current: PkgExclusion,
        val pkg: Pkg?,
    ) {
        val canRemove: Boolean = original != null
        val canSave: Boolean = original != current
    }

    companion object {
        private val TAG = logTag("Exclusion", "Editor", "Pkg", "ViewModel")
    }

}