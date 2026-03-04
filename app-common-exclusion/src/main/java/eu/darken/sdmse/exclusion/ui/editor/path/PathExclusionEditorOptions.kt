package eu.darken.sdmse.exclusion.ui.editor.path

import android.os.Parcelable
import eu.darken.sdmse.common.files.APath
import kotlinx.parcelize.Parcelize

@Parcelize
data class PathExclusionEditorOptions(
    val targetPath: APath
) : Parcelable