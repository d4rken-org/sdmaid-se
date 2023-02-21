package eu.darken.sdmse.automation.core.crawler

class BranchException(
    message: String,
    val altRoute: List<ACCrawler.Spec>,
    val invalidSteps: Int
) : CrawlerException(message)
