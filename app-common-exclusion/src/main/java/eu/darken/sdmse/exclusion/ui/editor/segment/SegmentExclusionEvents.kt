package eu.darken.sdmse.exclusion.ui.editor.segment

import eu.darken.sdmse.exclusion.core.types.SegmentExclusion

sealed class SegmentExclusionEvents {
    data class RemoveConfirmation(val exclusion: SegmentExclusion) : SegmentExclusionEvents()
    data class UnsavedChangesConfirmation(val exclusion: SegmentExclusion) : SegmentExclusionEvents()
}
