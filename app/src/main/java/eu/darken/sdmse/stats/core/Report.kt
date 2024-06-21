package eu.darken.sdmse.stats.core

import eu.darken.sdmse.main.core.SDMTool
import java.time.Instant
import java.util.UUID

interface Report {
    val reportId: ReportId
    val startAt: Instant
    val endAt: Instant
    val toolType: SDMTool.Type
    val status: Status

    enum class Status {
        SUCCESS,
        PARTIAL_SUCCESS,
        FAILURE
    }

    interface Details {
        val status: Status
    }
}

typealias ReportId = UUID