package eu.darken.sdmse.common.upgrade.ui

sealed class UpgradeEvents {
    data object RestoreFailed : UpgradeEvents()
}
