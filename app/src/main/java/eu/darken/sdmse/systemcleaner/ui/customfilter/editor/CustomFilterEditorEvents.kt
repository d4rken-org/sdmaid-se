package eu.darken.sdmse.systemcleaner.ui.customfilter.editor

import eu.darken.sdmse.systemcleaner.core.filter.custom.CustomFilterConfig

sealed class CustomFilterEditorEvents {
    data class RemoveConfirmation(val config: CustomFilterConfig) : CustomFilterEditorEvents()
    data class UnsavedChangesConfirmation(val config: CustomFilterConfig) : CustomFilterEditorEvents()
}
