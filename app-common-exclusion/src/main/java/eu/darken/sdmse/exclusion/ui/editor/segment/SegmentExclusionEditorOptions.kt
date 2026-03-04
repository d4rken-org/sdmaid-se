package eu.darken.sdmse.exclusion.ui.editor.segment

import android.os.Parcelable
import eu.darken.sdmse.common.files.Segments
import kotlinx.parcelize.Parcelize

@Parcelize
data class SegmentExclusionEditorOptions(
    val targetSegments: Segments? = null,
) : Parcelable