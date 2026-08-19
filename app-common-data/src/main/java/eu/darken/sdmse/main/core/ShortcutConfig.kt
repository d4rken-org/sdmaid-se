package eu.darken.sdmse.main.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Which tools get a launcher shortcut when long-pressing the app icon.
 *
 * The default reproduces the previously hardcoded AppControl shortcut, so existing installs keep
 * their published shortcut without a migration.
 */
@Serializable
data class ShortcutConfig(
    @SerialName("tools") val tools: List<DashboardCardType> = listOf(DashboardCardType.APPCONTROL),
)
