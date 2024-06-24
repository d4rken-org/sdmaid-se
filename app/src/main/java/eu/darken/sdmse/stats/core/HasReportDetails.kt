package eu.darken.sdmse.stats.core

interface HasReportDetails {
    val reportDetails: Report.Details?
        get() = null
}