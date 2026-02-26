package eu.darken.sdmse.common.picker

sealed class PickerEvent {
    data object ExitConfirmation : PickerEvent()
    data class Save(
        val requestKey: String,
        val result: PickerResult,
    ) : PickerEvent()
}
