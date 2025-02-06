package eu.darken.sdmse.common.picker

sealed class PickerEvents {
    data object ExitConfirmation : PickerEvents()
}
