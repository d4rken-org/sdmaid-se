@file:Suppress("UnusedReceiverParameter")

package eu.darken.sdmse.automation.core.specs

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.view.accessibility.AccessibilityEvent
import eu.darken.sdmse.automation.core.errors.NoSettingsWindowException
import eu.darken.sdmse.automation.core.AutomationEvent
import eu.darken.sdmse.automation.core.common.ACSNodeInfo
import eu.darken.sdmse.automation.core.common.crawl
import eu.darken.sdmse.automation.core.common.pkgId
import eu.darken.sdmse.automation.core.common.scrollNode
import eu.darken.sdmse.automation.core.common.scrollNodeBackward
import eu.darken.sdmse.automation.core.common.stepper.StepContext
import eu.darken.sdmse.automation.core.common.stepper.clickNormal
import eu.darken.sdmse.automation.core.common.stepper.findClickableParent
import eu.darken.sdmse.automation.core.common.textContainsAny
import eu.darken.sdmse.automation.core.common.textMatchesAny
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.toVisualString
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.getLabel2
import eu.darken.sdmse.common.pkgs.getPackageInfo2
import eu.darken.sdmse.common.pkgs.getSettingsIntent
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withTimeoutOrNull

fun SpecGenerator.windowLauncherDefaultSettings(
    pkgInfo: Installed
): suspend StepContext.() -> Unit = {
    val intent = pkgInfo.getSettingsIntent(androidContext).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
    }
    log(tag, INFO) { "Launching $intent" }
    host.service.startActivity(intent)
}

fun SpecGenerator.windowCheck(
    condition: suspend StepContext.(event: AutomationEvent?, root: ACSNodeInfo) -> Boolean,
): suspend StepContext.() -> ACSNodeInfo = {
    val events: Flow<AutomationEvent?> = host.events
    val (event, root) = events
        .onStart {
            // we may already be ready
            emit(null as AutomationEvent?)
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
): suspend StepContext.() -> ACSNodeInfo {
    val condition = interferenceAware(
        expectedPkgs = setOf(windowPkgId),
        targetPkg = pkgInfo.id,
        ipcFunnel = ipcFunnel,
    ) { _, root ->
        root.pkgId == windowPkgId && checkIdentifiers(ipcFunnel, pkgInfo)(root)
    }
    return {
        if (stepAttempts >= 1 && pkgInfo.hasNoSettings) {
            throw NoSettingsWindowException("${pkgInfo.packageName} has no settings window.")
        }
        windowCheck(condition)()
    }
}

/**
 * Wraps a window-check [condition] with [SettingsInterferenceDetector]: whenever the condition
 * does NOT match, the current window is checked for a foreign app (e.g. an app-locker) blocking
 * the [expectedPkgs] settings screen. One detector is created per call, so it accumulates sightings
 * across the stepper's inner retry loop. [targetPkg] is the app being processed.
 */
fun SpecGenerator.interferenceAware(
    expectedPkgs: Set<Pkg.Id>,
    targetPkg: Pkg.Id,
    ipcFunnel: IPCFunnel,
    condition: suspend StepContext.(event: AutomationEvent?, root: ACSNodeInfo) -> Boolean,
): suspend StepContext.(event: AutomationEvent?, root: ACSNodeInfo) -> Boolean {
    val detector = SettingsInterferenceDetector(
        expectedPkgs = expectedPkgs,
        targetPkg = targetPkg,
        resolveLabel = { pkg -> ipcFunnel.use { packageManager.getLabel2(pkg) } },
        isSystemApp = { pkg ->
            ipcFunnel.use {
                try {
                    val info = packageManager.getApplicationInfo(pkg.name, 0)
                    info.flags and ApplicationInfo.FLAG_SYSTEM != 0 ||
                            info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
                } catch (_: PackageManager.NameNotFoundException) {
                    // Unknown app - treat as non-system so generic detection can still fire.
                    false
                }
            }
        },
    )
    return { event, root ->
        // Detection runs before the predicate so an interfering window is caught even if the
        // predicate would otherwise throw (e.g. NoSettingsWindowException). On a matching or benign
        // root the detector just resets, so evaluating it first is safe.
        detector.evaluate(root, androidContext.packageName)
        condition(event, root)
    }
}

suspend fun SpecGenerator.checkIdentifiers(
    ipcFunnel: IPCFunnel,
    pkgInfo: Installed,
): suspend StepContext.(ACSNodeInfo) -> Boolean = { root ->
    val candidates = mutableSetOf(pkgInfo.packageName)

    // Use pkgInfo.label which handles archived packages correctly via ArchivedPackageInfo
    pkgInfo.label?.get(androidContext)?.let { candidates.add(it) }

    ipcFunnel
        .use { packageManager.getLabel2(pkgInfo.id) }
        ?.let { candidates.add(it) }

    ipcFunnel
        .use {
            val ai = try {
                packageManager.getApplicationInfo(pkgInfo.packageName, 0)
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
            if (ai == null) {
                log(tag, WARN) { "checkIdentifiers: PackageName not found: $pkgInfo" }
                return@use null
            }
            if (ai.labelRes == 0) {
                log(tag) { "checkIdentifiers: labelRes was 0 for $pkgInfo" }
                return@use null
            }

            getLocales().map { locale ->
                val res = packageManager.getResourcesForApplication(ai)

                @Suppress("DEPRECATION")
                val localRes = Resources(
                    res.assets,
                    res.displayMetrics,
                    Configuration().apply { setLocale(locale) }
                )
                localRes.getString(ai.labelRes)
            }
        }
        ?.let { candidates.addAll(it) }


    ipcFunnel
        .use {
            try {
                packageManager.getLaunchIntentForPackage(pkgInfo.packageName)?.component
                    ?.let { comp ->
                        packageManager
                            .getPackageInfo2(pkgInfo.id, PackageManager.GET_ACTIVITIES)
                            ?.activities
                            ?.singleOrNull { it.packageName == comp.packageName && it.name == comp.className }
                    }
                    ?.loadLabel(packageManager)
                    ?.toString()
            } catch (e: Throwable) {
                log(tag) { "checkIdentifiers: error for $pkgInfo: ${e.asLog()}" }
                null
            }
        }
        ?.let { candidates.add(it) }

    pkgInfo.applicationInfo?.className
        ?.let { candidates.add(it) }

    log(tag, VERBOSE) { "checkIdentifiers: Looking for: ${candidates.map { it.toVisualString() }}" }

    val passed = root.crawl().map { it.node }.any { toTest ->
        candidates.any { candidate ->
            val match = toTest.text == candidate || toTest.text?.contains(candidate) == true
            if (match) log(tag, INFO) { "checkIdentifiers: Passed ('$candidate' on ${toTest})" }
            match
        }
    }
    if (!passed) log(tag, WARN) { "checkIdentifiers: Window check failed." }
    passed
}

fun SpecGenerator.defaultFindAndClick(
    isDryRun: Boolean = false,
    maxNesting: Int = 6,
    finder: suspend StepContext.() -> ACSNodeInfo?,
): suspend StepContext.() -> Boolean = action@{
    val target = finder(this) ?: return@action false
    val mapped = findClickableParent(maxNesting = maxNesting, node = target) ?: return@action false
    clickNormal(isDryRun = isDryRun, mapped)
}

private const val BUSY_NODE_MAX_LENGTH = 30

fun SpecGenerator.defaultNodeRecovery(
    pkg: Installed,
    extraBusyLabels: Collection<String> = emptySet(),
): suspend StepContext.(ACSNodeInfo) -> Boolean {
    // One state holder per call: tracks scrolling across the stepper's inner retry loop.
    // A new step attempt relaunches the target window and resets its scroll position.
    var lastAttempt = -1
    var lastScrolledForward = false
    return recovery@{ root ->
        log(tag) { "Performing node recovery for ${pkg.id}" }

        if (stepAttempts != lastAttempt) {
            lastAttempt = stepAttempts
            lastScrolledForward = false
        }

        // Check for busy/loading indicators: "...", "…", or any text containing them (e.g. "Computing…")
        val busyNode = root.crawl().firstOrNull { crawled ->
            val text = crawled.node.text?.toString() ?: return@firstOrNull false
            if (text.length > BUSY_NODE_MAX_LENGTH) return@firstOrNull false
            crawled.node.textContainsAny(listOf("...", "…")) ||
                    (extraBusyLabels.isNotEmpty() && crawled.node.textMatchesAny(extraBusyLabels))
        }
        if (busyNode != null) {
            log(tag, VERBOSE) { "Found a busy-node, attempting recovery via delay: $busyNode" }
            delay(1000)
            root.refresh()
            return@recovery true
        }

        var scrolled = false
        var scrolledForward = false
        val scrollableNodes = root.crawl().filter { it.node.isScrollable }.toList()

        coroutineScope {
            // A successful scroll action returns before the node tree reflects it (e.g. OneUI serves
            // stale nodes for another ~50-250ms). Arm the settle watcher before scrolling, the event
            // stream has no replay and the content change may arrive before we'd get to subscribe.
            val settleWatcher = async(start = CoroutineStart.UNDISPATCHED) {
                host.events.first {
                    (it.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
                            it.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) &&
                            it.pkgId == root.pkgId
                }
            }

            // Try scrolling forward first
            for (crawled in scrollableNodes) {
                val success = crawled.node.scrollNode()
                if (success) {
                    scrolled = true
                    scrolledForward = true
                    crawled.node.refresh()
                }
            }

            if (!scrolled) {
                if (lastScrolledForward) {
                    // We just scrolled to the end of the list ourselves, the target should be on screen
                    // and the previous look may have hit a stale tree. Scrolling backward now would undo
                    // our own scrolling, so give the finder one more look first.
                    log(tag, VERBOSE) { "Forward scroll failed right after a successful one, we are at the list end" }
                } else {
                    // The screen may have opened already scrolled past the target
                    log(tag, VERBOSE) { "Forward scroll failed, trying backward scroll" }
                    for (crawled in scrollableNodes) {
                        val success = crawled.node.scrollNodeBackward()
                        if (success) {
                            scrolled = true
                            crawled.node.refresh()
                        }
                    }
                }
            }

            if (scrolled) {
                val settleEvent = withTimeoutOrNull(1000) { settleWatcher.await() }
                log(tag, VERBOSE) { "Scrolled, settled after event: $settleEvent" }
            }
            // Timing out the await() does not cancel the watcher itself
            settleWatcher.cancel()
        }

        lastScrolledForward = scrolledForward

        scrolled
    }
}