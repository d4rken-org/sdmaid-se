package eu.darken.sdmse.automation.core.crawler

open class CrawlerException(message: String?, cause: Throwable?) : Exception(message, cause) {
    constructor(message: String?) : this(message, null)
}