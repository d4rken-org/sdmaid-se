@file:Suppress("UnusedReceiverParameter")

package eu.darken.sdmse.automation.core.specs

import android.content.Intent
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import eu.darken.sdmse.appcleaner.core.automation.errors.NoSettingsWindowException
import eu.darken.sdmse.automation.core.common.crawl
import eu.darken.sdmse.automation.core.common.pkgId
import eu.darken.sdmse.automation.core.common.scrollNode
import eu.darken.sdmse.automation.core.common.stepper.StepContext
import eu.darken.sdmse.automation.core.common.stepper.clickNormal
import eu.darken.sdmse.automation.core.common.stepper.findClickableParent
import eu.darken.sdmse.automation.core.common.textMatchesAny
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.getLabel2
import eu.darken.sdmse.common.pkgs.getPackageInfo2
import eu.darken.sdmse.common.pkgs.getSettingsIntent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart

fun SpecGenerator.windowLauncherDefaultSettings(
    pkgInfo: Installed
): suspend StepContext.() -> Unit = {
    val intent = pkgInfo.getSettingsIntent(androidContext).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                Intent.FLAG_ACTIVITY_NO_ANIMATION
    }
    log(tag, INFO) { "Launching $intent" }
    host.service.startActivity(intent)
}

fun SpecGenerator.windowCheck(
    condition: suspend StepContext.(event: AccessibilityEvent?, root: AccessibilityNodeInfo) -> Boolean,
): suspend StepContext.() -> AccessibilityNodeInfo = {
    val events: Flow<AccessibilityEvent?> = host.events
    val (event, root) = events
        .onStart {
            // we may already be ready
            emit(null as AccessibilityEvent?)
        }
        .mapNotNull { event ->
            // Get a root for us to test
            val root = host.windowRoot()
            if (root == null) {
                log(tag, VERBOSE) { "windowRoot was NULL" }
                return@mapNotNull null
            }
            event to root
        }
        .filter { (event, root) -> condition(event, root) }
        .first()
    log(tag, VERBOSE) { "Check passed after event $event, root is $root" }
    root
}

fun SpecGenerator.windowCheckDefaultSettings(
    windowPkgId: Pkg.Id,
    ipcFunnel: IPCFunnel,
    pkgInfo: Installed
): suspend StepContext.() -> AccessibilityNodeInfo = {
    if (stepAttempts >= 1 && pkgInfo.hasNoSettings) {
        throw NoSettingsWindowException("${pkgInfo.packageName} has no settings window.")
    }
    windowCheck { _, root ->
        root.pkgId == windowPkgId && checkAppIdentifier(ipcFunnel, pkgInfo)(root)
    }()
}

suspend fun SpecGenerator.checkAppIdentifier(
    ipcFunnel: IPCFunnel,
    pkgInfo: Installed,
): suspend StepContext.(AccessibilityNodeInfo) -> Boolean = { root ->
    val candidates = mutableSetOf(pkgInfo.packageName)

    ipcFunnel
        .use { packageManager.getLabel2(pkgInfo.id) }
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
                log(tag) { "windowCriteriaAppIdentifier error for $pkgInfo: ${e.asLog()}" }
                null
            }
        }
        ?.let { candidates.add(it) }

    pkgInfo.applicationInfo?.className
        ?.let { candidates.add(it) }

    log(tag, VERBOSE) { "Looking for window identifiers: $candidates" }

    root.crawl().map { it.node }.any { toTest ->
        candidates.any { candidate -> toTest.text == candidate || toTest.text?.contains(candidate) == true }
    }
}

fun SpecGenerator.defaultFindAndClick(
    isDryRun: Boolean = false,
    maxNesting: Int = 6,
    finder: suspend StepContext.() -> AccessibilityNodeInfo?,
): suspend StepContext.() -> Boolean = action@{
    val target = finder(this) ?: return@action false
    val mapped = findClickableParent(maxNesting = maxNesting, node = target) ?: return@action false
    clickNormal(isDryRun = isDryRun, mapped)
}

fun SpecGenerator.defaultNodeRecovery(
    pkg: Installed
): suspend StepContext.(AccessibilityNodeInfo) -> Boolean = { root ->
    val busyNode = root.crawl().firstOrNull { it.node.textMatchesAny(listOf("...", "…")) }
    if (busyNode != null) {
        log(tag, VERBOSE) { "Found a busy-node, attempting recovery via delay: $busyNode" }
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