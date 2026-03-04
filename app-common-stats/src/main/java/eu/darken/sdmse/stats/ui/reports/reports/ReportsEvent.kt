package eu.darken.sdmse.stats.ui.reports

sealed interface ReportsEvent {
    data class ShowError(val msg: String) : ReportsEvent
}