package eu.darken.sdmse.automation.core.crawler

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import eu.darken.sdmse.automation.core.pkgId
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.getLabel2
import eu.darken.sdmse.common.pkgs.getPackageInfo2
import eu.darken.sdmse.common.pkgs.getSettingsIntent
import kotlinx.coroutines.delay

object CrawlerCommon {
    val TAG: String = logTag("Automation", "Crawler", "Common")

    fun defaultWindowIntent(context: Context, pkgInfo: Installed): Intent = pkgInfo.getSettingsIntent(context).apply {
        flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_NO_ANIMATION
    }

    fun defaultWindowFilter(pkgId: Pkg.Id): (AccessibilityEvent) -> Boolean {
        return fun(event: AccessibilityEvent): Boolean {
            return event.pkgId == pkgId
        }
    }

    fun defaultClick(
        isDryRun: Boolean = false
    ): (AccessibilityNodeInfo, Int) -> Boolean = { node: AccessibilityNodeInfo, _: Int ->
        log(TAG, VERBOSE) { "Clicking on ${node.toStringShort()}" }
        if (!node.isEnabled) throw IllegalStateException("Clickable target is disabled.")
        if (isDryRun) {
            node.performAction(AccessibilityNodeInfo.ACTION_SELECT)
        } else {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    fun clickableParent(
        maxNesting: Int = 6
    ): (AccessibilityNodeInfo) -> AccessibilityNodeInfo = { us ->
        us.findParentOrNull(maxNesting = maxNesting) { it.isClickable }
            ?: throw CrawlerException("No clickable parent found (within $maxNesting)")
    }

    fun clickableSibling(): (AccessibilityNodeInfo) -> AccessibilityNodeInfo = { us ->
        us.searchUp(maxNesting = 2) { it.isClickable }
    }

    fun windowCriteria(
        windowPkgId: Pkg.Id,
        extraTest: (AccessibilityNodeInfo) -> Boolean = { true }
    ): suspend (AccessibilityNodeInfo) -> Boolean = { node: AccessibilityNodeInfo ->
        node.pkgId == windowPkgId && extraTest(node)
    }

    suspend fun windowCriteriaAppIdentifier(
        windowPkgId: Pkg.Id,
        ipcFunnel: IPCFunnel,
        pkgInfo: Installed
    ): suspend (AccessibilityNodeInfo) -> Boolean {
        val candidates = mutableSetOf(pkgInfo.packageName)

        ipcFunnel
            .use {
                packageManager.getLabel2(pkgInfo.id)
            }
            ?.let { candidates.add(it) }

        ipcFunnel
            .use {
                try {
                    val activityInfo = packageManager.getPackageInfo2(pkgInfo.id, PackageManager.GET_ACTIVITIES)

                    packageManager.getLaunchIntentForPackage(pkgInfo.packageName)?.component
                        ?.let { comp ->
                            activityInfo?.activities?.singleOrNull {
                                it.packageName == comp.packageName && it.name == comp.className
                            }
                        }
                        ?.loadLabel(packageManager)
                        ?.toString()
                } catch (e: Throwable) {
                    log(TAG) { "windowCriteriaAppIdentifier error for $pkgInfo: ${e.asLog()}" }
                    null
                }
            }
            ?.let { candidates.add(it) }

        log(TAG, VERBOSE) { "Looking for window identifiers: $candidates" }
        return windowCriteria(windowPkgId) { node ->
            node.crawl().map { it.node }.any { toTest ->
                candidates.any { candidate -> toTest.text == candidate || toTest.text?.contains(candidate) == true }
            }
        }
    }

    fun getBranchException(exception: Throwable): BranchException? {
        return when (exception.cause) {
            null -> null
            is BranchException -> exception.cause as BranchException
            else -> getBranchException(exception.cause!!)
        }
    }

    fun getDefaultNodeRecovery(pkg: Installed): suspend (AccessibilityNodeInfo) -> Boolean = { root ->
        val busyNode = root.crawl().firstOrNull { it.node.textMatchesAny(listOf("...", "…")) }
        if (busyNode != null) {
            log(TAG, VERBOSE) { "Found a busy-node, attempting recovery via delay: $busyNode" }
            delay(1000)
            root.refresh()
            true
        } else {
            var scrolled = false
            root.crawl()
                .filter { it.node.isScrollable }
                .forEach {
                    val success = it.node.scrollNode()
                    if (success) {
                        scrolled = true
                        it.node.refresh()
                    }
                }
            scrolled
        }
    }
}