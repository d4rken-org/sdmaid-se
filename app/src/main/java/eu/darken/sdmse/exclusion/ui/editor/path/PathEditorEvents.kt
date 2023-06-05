package eu.darken.sdmse.exclusion.ui.editor.path

import eu.darken.sdmse.exclusion.core.types.PathExclusion

sealed class PathEditorEvents {
    data class RemoveConfirmation(val exclusion: PathExclusion) : PathEditorEvents()
    data class UnsavedChangesConfirmation(val exclusion: PathExclusion) : PathEditorEvents()
}
