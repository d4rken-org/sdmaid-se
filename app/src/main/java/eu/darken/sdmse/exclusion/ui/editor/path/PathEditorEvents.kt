package eu.darken.sdmse.exclusion.ui.editor.path

import eu.darken.sdmse.exclusion.core.types.Exclusion

sealed class PathEditorEvents {
    data class RemoveConfirmation(val exclusion: Exclusion.Path) : PathEditorEvents()
    data class UnsavedChangesConfirmation(val exclusion: Exclusion.Path) : PathEditorEvents()
}
