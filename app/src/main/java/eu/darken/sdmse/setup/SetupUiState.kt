package eu.darken.sdmse.setup

internal sealed interface SetupUiState {
    data object Loading : SetupUiState
    data class Cards(val items: List<SetupCardItem>) : SetupUiState
    data object Complete : SetupUiState
}
