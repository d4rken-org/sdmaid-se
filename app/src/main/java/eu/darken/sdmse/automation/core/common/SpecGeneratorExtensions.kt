@file:Suppress("UnusedReceiverParameter")

package eu.darken.sdmse.automation.core.common

import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Path
import android.graphics.Rect
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import eu.darken.sdmse.automation.core.common.Stepper.StepContext
import eu.darken.sdmse.automation.core.dispatchGesture
import eu.darken.sdmse.automation.core.errors.AutomationException
import eu.darken.sdmse.automation.core.errors.DisabledTargetException
import eu.darken.sdmse.automation.core.errors.PlanAbortException
import eu.darken.sdmse.automation.core.errors.UnclickableTargetException
import eu.darken.sdmse.automation.core.specs.SpecGenerator
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.getLabel2
import eu.darken.sdmse.common.pkgs.getPackageInfo2
import eu.darken.sdmse.common.pkgs.getSettingsIntent
import eu.darken.sdmse.common.pkgs.isSystemApp
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

fun SpecGenerator.clickableSelfOrParent(
    maxNesting: Int = 6
): suspend StepContext.(AccessibilityNodeInfo) -> AccessibilityNodeInfo = { us ->
    if (us.isClickable) {
        us
    } else {
        us.findParentOrNull(maxNesting = maxNesting) {
            it.isClickable
        } ?: throw AutomationException("No clickable self or parent found (within $maxNesting)")
    }
}

fun SpecGenerator.clickableParent(
    maxNesting: Int = 6
): suspend StepContext.(AccessibilityNodeInfo) -> AccessibilityNodeInfo = { us ->
    us.findParentOrNull(maxNesting = maxNesting) {
        log(tag, VERBOSE) { "isClickable? ${it.toStringShort()}" }
        it.isClickable
    } ?: throw AutomationException("No clickable parent found (within $maxNesting)")
}

fun SpecGenerator.clickableSibling(): (AccessibilityNodeInfo) -> AccessibilityNodeInfo = { us ->
    us.searchUp(maxNesting = 2) { it.isClickable }
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
) = windowCheck { _, root ->
    root.pkgId == windowPkgId && checkAppIdentifier(ipcFunnel, pkgInfo)(root)
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

fun SpecGenerator.getDefaultNodeRecovery(
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

fun SpecGenerator.defaultClick(
    isDryRun: Boolean = false,
    onDisabled: ((AccessibilityNodeInfo) -> Boolean)? = null,
): suspend StepContext.(AccessibilityNodeInfo) -> Boolean = clickFunc@{ node: AccessibilityNodeInfo ->
    log(tag, VERBOSE) { "defaultClick(isDryRun=$isDryRun): Clicking on ${node.toStringShort()}" }

    when {
        !node.isEnabled -> onDisabled?.invoke(node) ?: throw DisabledTargetException("Clickable target is disabled.")
        isDryRun -> node.performAction(AccessibilityNodeInfo.ACTION_SELECT)
        node.isClickable -> node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        else -> throw UnclickableTargetException("Target is not clickable")
    }
}

suspend fun SpecGenerator.gestureClick(): suspend StepContext.(AccessibilityNodeInfo) -> Boolean = { node ->
    val rect = Rect().apply { node.getBoundsInScreen(this) }
    val x = rect.centerX().toFloat()
    val y = rect.centerY().toFloat()
    val path = Path().apply {
        moveTo(x, y)
        lineTo(x + 1f, y + 1f)
    }
    val gesture = GestureDescription.Builder().apply {
        addStroke(GestureDescription.StrokeDescription(path, 0, ViewConfiguration.getTapTimeout().toLong()))
    }.build()

    log(tag, VERBOSE) { "gestureClick(): Waiting for passthrough..." }
    host.changeOptions { it.copy(passthrough = true) }
    host.state.filter { it.passthrough }.first()

    log(tag) { "gestureClick(): Performing CLICK gesture at X=$x,Y=$y" }
    val success = host.dispatchGesture(gesture)

    host.changeOptions { it.copy(passthrough = false) }
    host.state.filter { !it.passthrough }.first()

    success
}

fun SpecGenerator.getAospClearCacheClick(
    pkg: Installed,
    tag: String
): suspend StepContext.(AccessibilityNodeInfo) -> Boolean = scope@{ node ->
    log(tag, VERBOSE) { "Clicking on ${node.toStringShort()} for $pkg:" }

    try {
        defaultClick(isDryRun = Bugs.isDryRun)(node)
    } catch (e: DisabledTargetException) {
        log(tag) { "Can't click on the clear cache button because it was disabled, but why..." }
        val allButtonsAreDisabled = try {
            node.getRoot(maxNesting = 4).crawl().map { it.node }.all { !it.isClickyButton() || !it.isEnabled }
        } catch (e: Exception) {
            log(tag, WARN) { "Error while trying to determine why the clear cache button is not enabled." }
            false
        }

        when {
            hasApiLevel(31) && pkg.isSystemApp && allButtonsAreDisabled -> {
                // https://github.com/d4rken-org/sdmaid-se/issues/1178
                log(tag, WARN) { "Locked system app, can't click clear cache for ${pkg.installId}" }
                throw PlanAbortException("Locked system app, can't clear cache: ${pkg.installId}")
            }

            allButtonsAreDisabled -> {
                // https://github.com/d4rken/sdmaid-public/issues/3121
                log(tag, WARN) { "Clear cache button disabled (others are too), assuming size calculation " }
                val sleepTime = 250L * (attempt + 1)
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
