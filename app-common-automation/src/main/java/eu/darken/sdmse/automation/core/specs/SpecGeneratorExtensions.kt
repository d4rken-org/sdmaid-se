@file:Suppress("UnusedReceiverParameter")

package eu.darken.sdmse.automation.core.specs

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import eu.darken.sdmse.appcleaner.core.automation.errors.NoSettingsWindowException
import eu.darken.sdmse.automation.core.AutomationEvent
import eu.darken.sdmse.automation.core.common.ACSNodeInfo
import eu.darken.sdmse.automation.core.common.crawl
import eu.darken.sdmse.automation.core.common.pkgId
import eu.darken.sdmse.automation.core.common.scrollNode
import eu.darken.sdmse.automation.core.common.stepper.StepContext
import eu.darken.sdmse.automation.core.common.stepper.clickNormal
import eu.darken.sdmse.automation.core.common.stepper.findClickableParent
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
): suspend StepContext.() -> ACSNodeInfo = {
    if (stepAttempts >= 1 && pkgInfo.hasNoSettings) {
        throw NoSettingsWindowException("${pkgInfo.packageName} has no settings window.")
    }
    windowCheck { _, root ->
        root.pkgId == windowPkgId && checkIdentifiers(ipcFunnel, pkgInfo)(root)
    }()
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

fun SpecGenerator.defaultNodeRecovery(
    pkg: Installed
): suspend StepContext.(ACSNodeInfo) -> Boolean = { root ->
    log(tag) { "Performing node recovery for ${pkg.id}" }
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