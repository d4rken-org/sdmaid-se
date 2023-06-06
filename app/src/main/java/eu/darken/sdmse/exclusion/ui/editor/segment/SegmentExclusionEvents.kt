package eu.darken.sdmse.exclusion.ui.editor.segment

import eu.darken.sdmse.exclusion.core.types.Exclusion

sealed class SegmentExclusionEvents {
    data class RemoveConfirmation(val exclusion: Exclusion.Segment) : SegmentExclusionEvents()
    data class UnsavedChangesConfirmation(val exclusion: Exclusion.Segment) : SegmentExclusionEvents()
}
