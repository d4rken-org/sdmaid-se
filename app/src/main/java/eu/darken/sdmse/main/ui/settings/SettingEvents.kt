package eu.darken.sdmse.main.ui.settings

sealed class SettingEvents {
    data class ShowVersionInfo(val info: String) : SettingEvents()
}
