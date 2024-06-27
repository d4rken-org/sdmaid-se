package eu.darken.sdmse.stats.core

import eu.darken.sdmse.main.core.SDMTool
import java.time.Instant
import java.util.UUID

data class BaseReport(
    override val reportId: ReportId = UUID.randomUUID(),
    override val startAt: Instant,
    override val endAt: Instant,
    override val tool: SDMTool.Type,
    override val status: Report.Status,
    override val errorMessage: String?
) : Report