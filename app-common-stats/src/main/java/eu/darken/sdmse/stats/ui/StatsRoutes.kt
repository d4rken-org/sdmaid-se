package eu.darken.sdmse.stats.ui

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data object ReportsRoute

@Serializable
data class SpaceHistoryRoute(
    val storageId: String? = null,
)

@Serializable
data class AffectedFilesRoute(
    val reportId: String,
) {
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
) {
    constructor(uuid: UUID) : this(reportId = uuid.toString())

    val reportIdUUID: UUID
        get() = try {
            UUID.fromString(reportId)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("Invalid report ID: $reportId", e)
        }
}
