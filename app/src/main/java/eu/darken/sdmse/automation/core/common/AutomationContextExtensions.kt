@file:Suppress("UnusedReceiverParameter")

package eu.darken.sdmse.automation.core.common

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import eu.darken.sdmse.automation.core.errors.AutomationException
import eu.darken.sdmse.automation.core.errors.DisabledTargetException
import eu.darken.sdmse.automation.core.errors.PlanAbortException
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.getLabel2
import eu.darken.sdmse.common.pkgs.getPackageInfo2
import eu.darken.sdmse.common.pkgs.getSettingsIntent
import eu.darken.sdmse.common.pkgs.isSystemApp
import kotlinx.coroutines.delay
import java.util.Locale

private val TAG: String = logTag("Automation", "Crawler", "Common")

fun AutomationExplorer.Context.defaultWindowIntent(
    pkgInfo: Installed
): Intent = pkgInfo.getSettingsIntent(androidContext).apply {
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TASK or
            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
            Intent.FLAG_ACTIVITY_NO_ANIMATION
}

fun AutomationExplorer.Context.defaultWindowFilter(
    pkgId: Pkg.Id
): (AccessibilityEvent) -> Boolean = fun(event: AccessibilityEvent): Boolean {
    // We want to know that the settings window is open now
    if (event.pkgId != pkgId) return false
    return when (event.eventType) {
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> true
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> true
        else -> false
    }
}

fun AutomationExplorer.Context.defaultClick(
    isDryRun: Boolean = false,
    onDisabled: ((AccessibilityNodeInfo) -> Boolean)? = null,
): suspend (AccessibilityNodeInfo, Int) -> Boolean = clickFunc@{ node: AccessibilityNodeInfo, _: Int ->
    log(TAG, VERBOSE) { "Clicking (isDryRun=$isDryRun) on ${node.toStringShort()}" }

    when {
        !node.isEnabled -> onDisabled?.invoke(node) ?: throw DisabledTargetException("Clickable target is disabled.")
        isDryRun -> node.performAction(AccessibilityNodeInfo.ACTION_SELECT)
        else -> node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }
}

fun AutomationExplorer.Context.clickableSelfOrParent(
    maxNesting: Int = 6
): suspend (AccessibilityNodeInfo) -> AccessibilityNodeInfo = { us ->
    if (us.isClickable) {
        us
    } else {
        us.findParentOrNull(maxNesting = maxNesting) {
            it.isClickable
        } ?: throw AutomationException("No clickable self or parent found (within $maxNesting)")
    }
}

fun AutomationExplorer.Context.clickableParent(
    maxNesting: Int = 6
): suspend (AccessibilityNodeInfo) -> AccessibilityNodeInfo = { us ->
    us.findParentOrNull(maxNesting = maxNesting) {
        it.isClickable
    } ?: throw AutomationException("No clickable parent found (within $maxNesting)")
}

fun AutomationExplorer.Context.clickableSibling(): (AccessibilityNodeInfo) -> AccessibilityNodeInfo = { us ->
    us.searchUp(maxNesting = 2) { it.isClickable }
}

fun AutomationExplorer.Context.windowCriteria(
    windowPkgId: Pkg.Id,
    extraTest: (AccessibilityNodeInfo) -> Boolean = { true }
): suspend (AccessibilityNodeInfo) -> Boolean = { node: AccessibilityNodeInfo ->
    node.pkgId == windowPkgId && extraTest(node)
}

suspend fun AutomationExplorer.Context.windowCriteriaAppIdentifier(
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

    pkgInfo.applicationInfo?.className
        ?.let { candidates.add(it) }

    log(TAG, VERBOSE) { "Looking for window identifiers: $candidates" }
    return windowCriteria(windowPkgId) { node ->
        node.crawl().map { it.node }.any { toTest ->
            candidates.any { candidate -> toTest.text == candidate || toTest.text?.contains(candidate) == true }
        }
    }
}

fun AutomationExplorer.Context.getDefaultNodeRecovery(pkg: Installed): suspend (AccessibilityNodeInfo) -> Boolean =
    { root ->
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

fun AutomationExplorer.Context.getAospClearCacheClick(
    pkg: Installed,
    tag: String
): suspend (AccessibilityNodeInfo, Int) -> Boolean = scope@{ node, retryCount ->
    log(tag, VERBOSE) { "Clicking on ${node.toStringShort()} for $pkg:" }

    try {
        defaultClick(isDryRun = Bugs.isDryRun).invoke(node, retryCount)
    } catch (e: DisabledTargetException) {
        log(tag) { "Can't click on the clear cache button because it was disabled, but why..." }
        val allButtonsAreDisabled = try {
            node.getRoot(maxNesting = 4).crawl().map { it.node }.all { !it.isClickyButton() || !it.isEnabled }
        } catch (e: Exception) {
            log(tag, WARN) { "Error while trying to determine why the clear cache button is not enabled." }
            false
        }

        when {
            hasApiLevel(34) && pkg.isSystemApp && allButtonsAreDisabled -> {
                // https://github.com/d4rken-org/sdmaid-se/issues/1178
                log(TAG, WARN) { "Locked system app, can't click clear cache for ${pkg.installId}" }
                throw PlanAbortException("Locked system app, can't clear cache: ${pkg.installId}")
            }

            allButtonsAreDisabled -> {
                // https://github.com/d4rken/sdmaid-public/issues/3121
                log(tag, WARN) { "Clear cache button disabled (others are too), assuming size calculation " }
                val sleepTime = 250L * (retryCount + 1)
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

fun AutomationExplorer.Context.getSysLocale(): Locale {
    val locales = Resources.getSystem().configuration.locales
    log(INFO) { "getSysLocale(): $locales" }
    return locales[0]
}