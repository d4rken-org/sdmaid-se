@file:Suppress("UnusedReceiverParameter")

package eu.darken.sdmse.appcleaner.core.automation.specs

import android.view.accessibility.AccessibilityNodeInfo
import eu.darken.sdmse.appcleaner.core.automation.errors.LockedAppCacheException
import eu.darken.sdmse.automation.core.common.crawl
import eu.darken.sdmse.automation.core.common.getRoot
import eu.darken.sdmse.automation.core.common.isClickyButton
import eu.darken.sdmse.automation.core.common.stepper.StepContext
import eu.darken.sdmse.automation.core.common.stepper.clickNormal
import eu.darken.sdmse.automation.core.common.stepper.findNode
import eu.darken.sdmse.automation.core.common.toStringShort
import eu.darken.sdmse.automation.core.errors.DisabledTargetException
import eu.darken.sdmse.automation.core.specs.SpecGenerator
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.isSystemApp
import kotlinx.coroutines.delay

fun SpecGenerator.defaultFindAndClickClearCache(
    isDryRun: Boolean,
    pkg: Installed,
    predicate: suspend (AccessibilityNodeInfo) -> Boolean,
): suspend StepContext.() -> Boolean = scope@{
    val target = findNode(predicate) ?: return@scope false
    log(tag, VERBOSE) { "Clicking on ${target.toStringShort()} for $pkg:" }
    clickClearCache(isDryRun = isDryRun, pkg = pkg, node = target)
}

suspend fun StepContext.clickClearCache(
    isDryRun: Boolean,
    pkg: Installed,
    node: AccessibilityNodeInfo,
): Boolean {
    return try {
        clickNormal(isDryRun = isDryRun, node)
    } catch (e: DisabledTargetException) {
        log(tag) { "Can't click on the clear cache button because it was disabled, but why... $e" }
        val allButtonsAreDisabled = try {
            node.getRoot(maxNesting = 4).crawl().map { it.node }.all { !it.isClickyButton() || !it.isEnabled }
        } catch (e: Exception) {
            log(tag, WARN) { "Error while trying to determine why the clear cache button is not enabled: ${e.asLog()}" }
            false
        }

        when {
            hasApiLevel(30) && pkg.isSystemApp -> {
                // https://github.com/d4rken-org/sdmaid-se/issues/1178
                log(tag, WARN) { "Locked system app, can't click clear cache for ${pkg.installId}" }
                throw LockedAppCacheException("Locked system app, can't clear cache: ${pkg.installId}")
            }

            allButtonsAreDisabled -> {
                // https://github.com/d4rken/sdmaid-public/issues/3121
                log(tag, WARN) { "Clear cache button disabled (others are too), assuming size calculation " }
                val sleepTime = 250L * (stepAttempts + 1)
                log(tag) { "Sleeping for $sleepTime to wait for calculation." }
                delay(sleepTime)
                false
            }

            else -> {
                // https://github.com/d4rken/sdmaid-public/issues/2517
                log(tag, WARN) { "Only clear cache was disabled, assuming stale infos, counting as success." }
                true
            }
        }
    }
}