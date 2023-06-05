package eu.darken.sdmse.exclusion.ui.editor.pkg

import eu.darken.sdmse.exclusion.core.types.PkgExclusion

sealed class PkgExclusionEvents {
    data class RemoveConfirmation(val exclusion: PkgExclusion) : PkgExclusionEvents()
    data class UnsavedChangesConfirmation(val exclusion: PkgExclusion) : PkgExclusionEvents()
}
