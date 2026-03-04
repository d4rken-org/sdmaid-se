package eu.darken.sdmse.exclusion.ui.editor.pkg

import android.os.Parcelable
import eu.darken.sdmse.common.pkgs.Pkg
import kotlinx.parcelize.Parcelize

@Parcelize
data class PkgExclusionEditorOptions(
    val targetPkgId: Pkg.Id,
) : Parcelable