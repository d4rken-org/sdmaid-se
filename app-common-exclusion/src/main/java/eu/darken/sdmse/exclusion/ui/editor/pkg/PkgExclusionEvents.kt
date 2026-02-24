package eu.darken.sdmse.exclusion.ui.editor.pkg

import eu.darken.sdmse.exclusion.core.types.Exclusion

sealed class PkgExclusionEvents {
    data class RemoveConfirmation(val exclusion: Exclusion.Pkg) : PkgExclusionEvents()
    data class UnsavedChangesConfirmation(val exclusion: Exclusion.Pkg) : PkgExclusionEvents()
}
