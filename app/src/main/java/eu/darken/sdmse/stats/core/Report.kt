package eu.darken.sdmse.stats.core

import eu.darken.sdmse.main.core.SDMTool
import java.time.Instant
import java.util.UUID

interface Report {
    val reportId: ReportId
    val startAt: Instant
    val endAt: Instant
    val tool: SDMTool.Type
    val status: Status
    val errorMessage: String?

    val affectedCount: Int?
    val affectedSpace: Long?
    val extra: String?

    enum class Status {
        SUCCESS,
        PARTIAL_SUCCESS,
        FAILURE
    }
}

typealias ReportId = UUID