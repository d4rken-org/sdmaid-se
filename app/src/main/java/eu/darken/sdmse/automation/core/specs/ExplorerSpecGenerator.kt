package eu.darken.sdmse.automation.core.specs

import android.view.accessibility.AccessibilityNodeInfo
import eu.darken.sdmse.automation.core.common.CrawlerCommon
import eu.darken.sdmse.automation.core.common.crawl
import eu.darken.sdmse.automation.core.common.getRoot
import eu.darken.sdmse.automation.core.common.isClickyButton
import eu.darken.sdmse.automation.core.common.toStringShort
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.pkgs.features.Installed

abstract class ExplorerSpecGenerator : SpecGenerator {

    abstract val tag: String

    fun getDefaultFinalClick(
        pkg: Installed,
        tag: String
    ): (AccessibilityNodeInfo, Int) -> Boolean = scope@{ node, retryCount ->
        log(tag, VERBOSE) { "Clicking on ${node.toStringShort()} for $pkg:" }

        if (Bugs.isDryRun) {
            log(tag, WARN) { "DRYRUN: Not clicking ${node.toStringShort()}" }
            return@scope true
        }

        val success = CrawlerCommon.defaultClick(isDryRun = Bugs.isDryRun).invoke(node, retryCount)
        if (!success && !node.isEnabled) {
            log(tag) { "Can't click on the clear cache button because it was disabled, but why..." }
            try {
                val allButtonsAreDisabled = node.getRoot(maxNesting = 4).crawl().map { it.node }.all {
                    !it.isClickyButton() || !it.isEnabled
                }
                if (allButtonsAreDisabled) {
                    // https://github.com/d4rken/sdmaid-public/issues/3121
                    log(tag, WARN) {
                        "Clear cache button was disabled, but so are others, assuming size calculation going on."
                    }
                    log(tag) { "Sleeping for 1000ms to wait for calculation." }
                    Thread.sleep((500 * retryCount).toLong())
                    false
                } else {
                    // https://github.com/d4rken/sdmaid-public/issues/2517
                    log(tag, WARN) {
                        "Only the clear cache button was disabled, assuming stale information, counting as success."
                    }
                    true
                }
            } catch (e: Exception) {
                log(tag, WARN) { "Error while trying to determine why the clear cache button is not enabled." }
                false
            }
        } else {
            success
        }
    }
}