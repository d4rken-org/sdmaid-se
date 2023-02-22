package eu.darken.sdmse.automation.core.crawler

class BranchException(
    message: String,
    val altRoute: List<AutomationCrawler.Step>,
    val invalidSteps: Int
) : CrawlerException(message)
