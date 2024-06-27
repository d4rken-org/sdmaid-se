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

    enum class Status {
        SUCCESS,
        PARTIAL_SUCCESS,
        FAILURE
    }

    interface Details {
        val status: Status
            get() = Status.SUCCESS

        val failureReason: String?
            get() = null

        interface SpaceFreed : Details {
            val spaceFreed: Long
        }

        interface ItemsProcessed : Details {
            val processedCount: Int
        }
    }
}

typealias ReportId = UUID