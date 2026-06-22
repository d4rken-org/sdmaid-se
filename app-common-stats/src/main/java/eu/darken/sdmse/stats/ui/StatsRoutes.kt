package eu.darken.sdmse.stats.ui

import eu.darken.sdmse.common.navigation.NavigationDestination
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data object StatsSettingsRoute : NavigationDestination

@Serializable
data object ReportsRoute : NavigationDestination

@Serializable
data class SpaceHistoryRoute(
    val storageId: String? = null,
) : NavigationDestination

@Serializable
data class AffectedFilesRoute(
    val reportId: String,
) : NavigationDestination {
    constructor(uuid: UUID) : this(reportId = uuid.toString())
    val reportIdUUID: UUID
        get() = try {
            UUID.fromString(reportId)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("Invalid report ID: $reportId", e)
        }
}

@Serializable
data class AffectedPkgsRoute(
    val reportId: String,
) : NavigationDestination {
    constructor(uuid: UUID) : this(reportId = uuid.toString())

    val reportIdUUID: UUID
        get() = try {
            UUID.fromString(reportId)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("Invalid report ID: $reportId", e)
        }
}
