package eu.darken.sdmse.exclusion.ui.editor.pkg

import eu.darken.sdmse.exclusion.core.types.PackageExclusion

sealed class PkgExclusionEvents {
    data class RemoveConfirmation(val exclusion: PackageExclusion) : PkgExclusionEvents()
    data class UnsavedChangesConfirmation(val exclusion: PackageExclusion) : PkgExclusionEvents()
}
