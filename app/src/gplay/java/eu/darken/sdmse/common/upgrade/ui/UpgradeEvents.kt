package eu.darken.sdmse.common.upgrade.ui

sealed class UpgradeEvents {
    data object RestoreSucceeded : UpgradeEvents()
    data object RestoreFailed : UpgradeEvents()
    data object SubscriptionStillRenewing : UpgradeEvents()
    data object SubscriptionCheckFailed : UpgradeEvents()
}
