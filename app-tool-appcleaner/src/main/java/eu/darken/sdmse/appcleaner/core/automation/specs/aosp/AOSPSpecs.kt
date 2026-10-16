package eu.darken.sdmse.appcleaner.core.automation.specs.aosp

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.appcleaner.R
import eu.darken.sdmse.appcleaner.core.automation.errors.LockedAppCacheException
import eu.darken.sdmse.appcleaner.core.automation.specs.AppCleanerSpecGenerator
import eu.darken.sdmse.appcleaner.core.automation.specs.StorageEntryFinder
import eu.darken.sdmse.appcleaner.core.automation.specs.clickClearCache
import eu.darken.sdmse.appcleaner.core.scanner.InaccessibleCacheProvider
import eu.darken.sdmse.automation.core.common.ACSNodeInfo
import eu.darken.sdmse.automation.core.common.contentDescMatches
import eu.darken.sdmse.automation.core.common.crawl
import eu.darken.sdmse.automation.core.common.isClickyButton
import eu.darken.sdmse.automation.core.common.isTextView
import eu.darken.sdmse.automation.core.common.stepper.AutomationStep
import eu.darken.sdmse.automation.core.common.stepper.StepContext
import eu.darken.sdmse.automation.core.common.stepper.Stepper
import eu.darken.sdmse.automation.core.common.stepper.findClickableParent
import eu.darken.sdmse.automation.core.common.stepper.findClickableSibling
import eu.darken.sdmse.automation.core.common.stepper.findFocusedNode
import eu.darken.sdmse.automation.core.common.stepper.findNodeByContentDesc
import eu.darken.sdmse.automation.core.common.stepper.findNodeByLabel
import eu.darken.sdmse.automation.core.common.stepper.waitForLayoutStability
import eu.darken.sdmse.automation.core.common.textMatches
import eu.darken.sdmse.automation.core.errors.StepAbortException
import eu.darken.sdmse.automation.core.input.InputInjector
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.automation.core.specs.AutomationSpec
import eu.darken.sdmse.automation.core.specs.defaultFindAndClick
import eu.darken.sdmse.automation.core.specs.defaultNodeRecovery
import eu.darken.sdmse.automation.core.specs.windowCheckDefaultSettings
import eu.darken.sdmse.automation.core.specs.windowLauncherDefaultSettings
import eu.darken.sdmse.common.BuildWrap
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.debug.toVisualStrings
import eu.darken.sdmse.common.device.DeviceDetective
import eu.darken.sdmse.common.device.RomType
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.isSystemApp
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.progress.withProgress
import eu.darken.sdmse.common.ui.SizeParser
import eu.darken.sdmse.common.device.RomTypeProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.math.ceil

@Reusable
class AOSPSpecs @Inject constructor(
    private val ipcFunnel: IPCFunnel,
    private val deviceDetective: DeviceDetective,
    private val aospLabels: AOSPLabels,
    private val storageEntryFinder: StorageEntryFinder,
    private val romTypeProvider: RomTypeProvider,
    private val stepper: Stepper,
    private val inputInjector: InputInjector,
    private val inaccessibleCacheProvider: InaccessibleCacheProvider,
) : AppCleanerSpecGenerator {

    override val tag: String = TAG

    override suspend fun isResponsible(pkg: Installed): Boolean {
        val romType = romTypeProvider.getRomType()
        if (romType == RomType.AOSP) return true
        if (romType != RomType.AUTO) return false

        return deviceDetective.getROMType() == RomType.AOSP
    }

    override suspend fun getClearCache(pkg: Installed): AutomationSpec = object : AutomationSpec.Explorer {
        override val tag: String = TAG
        override suspend fun createPlan(): suspend AutomationExplorer.Context.() -> Unit = {
            mainPlan(pkg)
        }
    }

    private suspend fun StepContext.findClearCacheCandidate(
        labels: Collection<String>,
    ): ACSNodeInfo? {
        findNodeByLabel(labels)?.let {
            log(tag) { "Found candidate by text: $it" }
            return it
        }

        if (hasApiLevel(36)) {
            findNodeByContentDesc(labels) { it.isClickable }?.let {
                log(tag, INFO) { "Found candidate by content-desc: $it" }
                return it
            }
        }

        return null
    }

    private suspend fun StepContext.resolveClickTarget(candidate: ACSNodeInfo): ACSNodeInfo? {
        if (candidate.isClickyButton()) {
            log(tag) { "Target is clicky button: $candidate" }
            return candidate
        }

        // If candidate is clickable and not a TextView, use it directly
        // This handles content-desc matches where the node IS the target (e.g., action2 LinearLayout)
        if (candidate.isClickable && !candidate.isTextView()) {
            log(tag) { "Target is clickable non-TextView: $candidate" }
            return candidate
        }

        // For TextViews or non-clickable nodes, look for clickable parent/sibling
        findClickableParent(node = candidate)?.let {
            log(tag) { "Target is clickable parent: $it" }
            return it
        }

        findClickableSibling(node = candidate)?.let {
            log(tag) { "Target is clickable sibling: $it" }
            return it
        }

        return null
    }

    /**
     * Reads the cache size Settings itself shows on the app's storage page.
     *
     * Returns null when the row can't be located or its value can't be parsed, which includes the
     * "still calculating" state, so callers must treat null as "don't know" and never as "not zero".
     * Reads [root] when given, the current window otherwise.
     */
    private suspend fun StepContext.readCacheRowSize(
        cacheSizeLabels: Collection<String>,
        sizeParser: SizeParser?,
        root: ACSNodeInfo? = null,
    ): Long? {
        if (sizeParser == null) {
            log(tag, WARN) { "readCacheRowSize(): SizeParser unavailable" }
            return null
        }
        val title = if (root != null) {
            root.findRowTitle(cacheSizeLabels)
        } else {
            findNodeByLabel(cacheSizeLabels) { it.viewIdResourceName == ROW_TITLE_ID }
        }
        if (title == null) {
            log(tag, VERBOSE) { "readCacheRowSize(): no cache row title" }
            return null
        }
        val raw = title.findRowSummaryText()
        if (raw == null) {
            log(tag, VERBOSE) { "readCacheRowSize(): cache row has no summary" }
            return null
        }
        return runCatching { sizeParser.parse(raw) }.getOrNull()
            .also { log(tag, INFO) { "readCacheRowSize(): '$raw' -> $it" } }
    }

    private suspend fun StepContext.validateClickEffect(
        source: String,
        timeoutMs: Long = 3000,
    ): Boolean {
        if (Bugs.isDryRun) return true

        val qualifyingTypes = setOf(
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
        )
        val event = withTimeoutOrNull(timeoutMs) {
            host.events.first { it.eventType in qualifyingTypes && it.pkgId == SETTINGS_PKG }
        }

        val root = host.windowRoot()
        val rootPkg = root?.packageName

        val focus = findFocusedNode()
        log(tag, INFO) {
            "validateClickEffect[$source]: event=${event?.eventType}, rootPkg=$rootPkg, focused=${focus.inputFocused ?: focus.accessibilityFocused}"
        }

        if (event == null) {
            log(tag, WARN) { "validateClickEffect[$source]: no_event" }
            return false
        }
        if (rootPkg != SETTINGS_PKG.name) {
            log(tag, WARN) { "validateClickEffect[$source]: wrong_root_pkg=$rootPkg" }
            return false
        }

        log(tag, INFO) { "validateClickEffect[$source]: ok" }
        return true
    }

    // Snapshots capture storage size values from the Settings UI for delta comparison.
    // Unparseable rows are preserved as ParsedSize(null, raw) so that snapshot sizes remain
    // stable across pre/post comparisons. This matters when cache clears to a locale-specific
    // zero format that SizeParser can't handle (e.g., "0 o" in French).
    private fun StepContext.takeStorageSnapshot(sizeParser: SizeParser?, root: ACSNodeInfo?): StorageSnapshot {
        if (sizeParser == null) {
            log(tag, WARN) { "takeStorageSnapshot(): SizeParser unavailable" }
            return StorageSnapshot(emptyList())
        }
        val nodes = root?.crawl()?.map { it.node }
            ?.filter { it.viewIdResourceName == "android:id/summary" }
            ?: emptySequence()
        val values = nodes.mapNotNull { node ->
            val raw = node.text?.toString() ?: return@mapNotNull null
            val parsed = runCatching { sizeParser.parse(raw) }.getOrNull()
            if (parsed == null) {
                log(tag, WARN) { "takeStorageSnapshot(): failed to parse '$raw'" }
            }
            StorageSnapshot.ParsedSize(parsed, raw)
        }.toList()
        log(tag) { "takeStorageSnapshot(): ${values.size} values: $values" }
        return StorageSnapshot(values)
    }

    // Delta validation compares storage snapshots taken before and after a click to detect
    // whether cache was actually cleared. This is critical for DPAD fallback where we can't
    // directly observe button state. Values may become unparseable after clearing (e.g.,
    // "238 Mo" → "0 o" in French) — compareSnapshots treats this as a decrease.
    private suspend fun StepContext.validateWithDelta(
        source: String,
        preSnapshot: StorageSnapshot,
        sizeParser: SizeParser?,
        timeoutMs: Long = 3000,
        stats: StatsMode? = null,
        statsPolls: Int = STATS_SHORT_POLLS,
    ): Boolean {
        if (Bugs.isDryRun) return true

        if (stats != null) return validateWithStats(source, stats, preSnapshot, sizeParser, timeoutMs, statsPolls)

        val eventOk = validateClickEffect(source, timeoutMs)
        if (!eventOk) return false

        // The first Settings event can be the pressed button's own content change, before a dialog
        // window is attached, so the anchor has to hold on every poll, not just at the event.
        if (host.windowRoot()?.hasEntityHeader() != true) {
            log(tag, WARN) { "Delta[$source]: anchor gone after the event" }
            return false
        }

        // No parseable values → can't compare, degrade to event-only validation
        val eventOnly = preSnapshot.values.isEmpty()
        if (eventOnly) log(tag, INFO) { "Delta[$source]: no pre-snapshot values, only watching the anchor" }

        // Retry snapshot comparison: the Settings UI may take a few hundred ms to update
        // accessibility node values after the click (observed ~340ms on Android 17 beta).
        val maxAttempts = 3
        val retryDelayMs = 250L
        for (attempt in 1..maxAttempts) {
            delay(retryDelayMs)
            val root = host.windowRoot()
            if (root?.hasEntityHeader() != true) {
                log(tag, WARN) { "Delta[$source]: anchor gone (attempt=$attempt/$maxAttempts)" }
                return false
            }
            if (eventOnly) continue
            val postSnapshot = takeStorageSnapshot(sizeParser, root)
            when (compareSnapshots(preSnapshot, postSnapshot)) {
                DeltaResult.SUCCESS -> {
                    log(tag, INFO) { "Delta[$source]: values decreased (attempt=$attempt)" }
                    return true
                }
                DeltaResult.SKIP_SUCCESS -> {
                    log(tag, INFO) { "Delta[$source]: cache already zero (attempt=$attempt)" }
                    return true
                }
                DeltaResult.NO_CHANGE -> {
                    log(tag, WARN) { "Delta[$source]: no change (attempt=$attempt/$maxAttempts)" }
                }
                DeltaResult.INCONCLUSIVE -> {
                    log(tag, WARN) { "Delta[$source]: inconclusive (attempt=$attempt/$maxAttempts)" }
                }
            }
        }
        if (eventOnly) {
            log(tag, INFO) { "Delta[$source]: anchor held for ${maxAttempts * retryDelayMs}ms, trusting event" }
            return true
        }
        return false
    }

    private class StatsMode(
        val reader: CacheStatsReader,
        val baseline: CacheStats.Positive,
        val cacheRow: CacheRowBaseline?,
    )

    // Settings' Cache row, when the storage page showed one with a positive size before the first key.
    private class CacheRowBaseline(val labels: Collection<String>, val sizeParser: SizeParser, val bytes: Long)

    private suspend fun StepContext.cacheRowDropped(row: CacheRowBaseline, root: ACSNodeInfo): Boolean {
        val now = readCacheRowSize(row.labels, row.sizeParser, root) ?: return false
        return now < row.bytes
    }

    // Decided by the target's cache bytes or by the Cache row, whichever drops first. The Cache row can be
    // missing from the accessibility tree (as on a Pixel 9 Pro with Android 17) and "User data" excludes
    // the cache, so a press that cleared it can leave every row unchanged. StorageStats in turn can keep
    // the old size for more than 10s while the Cache row already reads "0 Byte" (Pixel 7 and Pixel 10 Pro,
    // Android 17). Other rows never decide: a zero or unparseable row says nothing about the cache. The
    // event is waited for in parallel, for the log only.
    private suspend fun StepContext.validateWithStats(
        source: String,
        stats: StatsMode,
        preSnapshot: StorageSnapshot,
        sizeParser: SizeParser?,
        eventTimeoutMs: Long,
        polls: Int,
    ): Boolean = coroutineScope {
        val eventLog = launch { validateClickEffect(source, eventTimeoutMs) }
        try {
            val windowMs = polls * STATS_POLL_DELAY_MS + STATS_POLL_READ_TIMEOUT_MS
            val dropped = withTimeoutOrNull(windowMs) {
                for (poll in 1..polls) {
                    delay(STATS_POLL_DELAY_MS)
                    val now = stats.reader.read(STATS_POLL_READ_TIMEOUT_MS)
                    log(tag, INFO) { "CacheStats[$source]: baseline=${stats.baseline.bytes}, now=$now (poll $poll/$polls)" }
                    val root = host.windowRoot()
                    if (root?.hasEntityHeader() != true) {
                        log(tag, WARN) { "CacheStats[$source]: anchor gone (poll $poll/$polls)" }
                        return@withTimeoutOrNull false
                    }
                    if (now.isBelow(stats.baseline)) return@withTimeoutOrNull true
                    val row = stats.cacheRow
                    if (row != null && cacheRowDropped(row, root)) {
                        log(tag, INFO) { "CacheRow[$source]: below ${row.bytes} (poll $poll/$polls)" }
                        return@withTimeoutOrNull true
                    }
                    if (preSnapshot.values.isNotEmpty()) {
                        val ui = compareSnapshots(preSnapshot, takeStorageSnapshot(sizeParser, root))
                        log(tag, INFO) { "Delta[$source]: $ui, not used in stats mode (poll $poll/$polls)" }
                    }
                }
                false
            }
            if (dropped == null) log(tag, WARN) { "CacheStats[$source]: no drop within ${windowMs}ms" }
            dropped == true
        } finally {
            eventLog.cancel()
        }
    }

    private fun ACSNodeInfo.hasEntityHeader(): Boolean =
        crawl().map { it.node }.any { it.viewIdResourceName == ENTITY_HEADER_CONTENT_ID }

    // A DPAD_CENTER that didn't clear the cache may have opened something else, e.g. Settings'
    // "Delete app data?" dialog or the app's own manage-space screen. Never sends a DPAD key.
    private suspend fun StepContext.containStrayPress() {
        var polledRoot: ACSNodeInfo? = null
        for (poll in 0..CONTAINMENT_POLLS) {
            polledRoot = host.windowRoot()
            if (polledRoot != null || poll == CONTAINMENT_POLLS) break
            delay(CONTAINMENT_POLL_DELAY_MS)
        }
        val root = polledRoot
        if (root == null) {
            log(tag, INFO) { "Containment: no window root, state unknown, no BACK sent" }
            return
        }
        if (root.hasEntityHeader()) {
            log(tag, INFO) { "Containment: storage page still shown, nothing to contain" }
            return
        }

        val wentBack = host.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        var anchorReturned = false
        for (poll in 1..CONTAINMENT_POLLS) {
            delay(CONTAINMENT_POLL_DELAY_MS)
            if (host.windowRoot()?.hasEntityHeader() == true) {
                anchorReturned = true
                break
            }
        }
        log(tag, INFO) {
            "Containment: GLOBAL_ACTION_BACK result=$wentBack from rootPkg=${root.packageName}, anchorReturned=$anchorReturned"
        }
    }

    // Equal anchor reads don't mean the window is at rest: during Settings' entry transition the anchor
    // read the same twice while the root still sat at Rect(217, 0 - 1177, 2142) on a 960px display.
    // A root reaching past the display's horizontal bounds is still moving. With the display bounds
    // unknown, every root counts as at rest.
    private fun StepContext.windowAtRestCheck(): suspend (ACSNodeInfo) -> Boolean {
        val display = readDisplayBounds()
        log(tag, INFO) { "DPAD rest check: display=$display" }
        if (display == null) return { true }
        return { root -> root.getScreenBounds().let { it.left >= display.left && it.right <= display.right } }
    }

    private fun StepContext.readDisplayBounds(): ACSNodeInfo.ScreenBounds? {
        if (!hasApiLevel(30)) return null
        return try {
            host.service.getSystemService(WindowManager::class.java)
                ?.maximumWindowMetrics
                ?.bounds
                ?.takeIf { it.right - it.left > 0 }
                ?.let { ACSNodeInfo.ScreenBounds(it.left, it.top, it.right, it.bottom) }
        } catch (e: Exception) {
            log(tag, WARN) { "DPAD rest check: display bounds unavailable: $e" }
            null
        }
    }

    /**
     * Returns null when [preClickCheck] rejected the screen after layout stabilization, before any
     * key was sent.
     *
     * The key sequence runs under [deadlineMs], cut down to what is left of the step attempt minus
     * [POST_RUN_RESERVE_MS], and running into it counts as exhausted. If the sequence ends without
     * success after a DPAD_CENTER, [containStrayPress] runs afterwards, outside the deadline,
     * followed by one more read of [cacheStats] and of the Cache row when the run had a cache baseline:
     * both can lag a clear past the last poll.
     */
    private suspend fun StepContext.tryClickViaFocusNavigation(
        labels: Collection<String>,
        target: Installed,
        cacheStats: CacheStatsReader,
        canInjectInput: Boolean,
        threeButtonRow: Boolean,
        settingsHasManageStorageText: Boolean,
        cacheSizeLabels: Collection<String>,
        preClickCheck: (suspend () -> Boolean)? = null,
        deadlineMs: Long = DPAD_RUN_DEADLINE_MS,
    ): Boolean? {
        log(tag, INFO) { "Trying DPAD navigation (anchor-based, inputInjection=$canInjectInput)" }

        if (!canInjectInput && !hasApiLevel(33)) {
            log(tag, WARN) { "DPAD global actions require API 33+" }
            return false
        }

        val budgetMs = attemptDeadline
            ?.let { minOf(deadlineMs, -it.elapsedNow().inWholeMilliseconds - POST_RUN_RESERVE_MS) }
            ?: deadlineMs
        if (budgetMs <= 0) {
            log(tag, WARN) { "DPAD skipped, the step attempt has no time left for a run (budget=${budgetMs}ms)" }
            return false
        }

        var centerSent = false
        var finished = false
        var stats: StatsMode? = null
        val result = try {
            withTimeoutOrNull(budgetMs) {
                runFocusNavigation(
                    labels = labels,
                    target = target,
                    cacheStats = cacheStats,
                    canInjectInput = canInjectInput,
                    threeButtonRow = threeButtonRow,
                    settingsHasManageStorageText = settingsHasManageStorageText,
                    cacheSizeLabels = cacheSizeLabels,
                    preClickCheck = preClickCheck,
                    onStatsMode = { stats = it },
                    onCenter = { centerSent = true },
                ).also { finished = true }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (centerSent) containStrayPress()
            throw e
        }

        if (!finished) log(tag, WARN) { "DPAD run hit its ${budgetMs}ms deadline, counting it as exhausted" }
        val runStats = stats
        if (result != true && centerSent) {
            containStrayPress()
            if (runStats != null) {
                val now = cacheStats.read(FINAL_RECHECK_READ_TIMEOUT_MS)
                val dropped = now.isBelow(runStats.baseline)
                log(tag, INFO) {
                    "CacheStats[final-check]: baseline=${runStats.baseline.bytes}, now=$now, dropped=$dropped"
                }
                val root = host.windowRoot()
                val onStoragePage = root != null && root.hasEntityHeader()
                val row = runStats.cacheRow
                val rowDropped = row != null && root != null && cacheRowDropped(row, root)
                if (row != null) log(tag, INFO) { "CacheRow[final-check]: baseline=${row.bytes}, dropped=$rowDropped" }
                if (dropped || rowDropped) {
                    if (onStoragePage) return true
                    log(tag, WARN) { "Final check: dropped but the storage page is not shown" }
                }
            }
        }
        return if (finished) result else false
    }

    @SuppressLint("InlinedApi")
    private suspend fun StepContext.runFocusNavigation(
        labels: Collection<String>,
        target: Installed,
        cacheStats: CacheStatsReader,
        canInjectInput: Boolean,
        threeButtonRow: Boolean,
        settingsHasManageStorageText: Boolean,
        cacheSizeLabels: Collection<String>,
        preClickCheck: (suspend () -> Boolean)?,
        onStatsMode: (StatsMode) -> Unit,
        onCenter: () -> Unit,
    ): Boolean? {
        val dpadDown: suspend () -> Boolean = if (canInjectInput) {
            { inputInjector.inject(InputInjector.Event.DpadDown); true }
        } else {
            { host.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_DOWN) }
        }
        val dpadRight: suspend () -> Boolean = if (canInjectInput) {
            { inputInjector.inject(InputInjector.Event.DpadRight); true }
        } else {
            { host.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT) }
        }
        val dpadLeft: suspend () -> Boolean = if (canInjectInput) {
            { inputInjector.inject(InputInjector.Event.DpadLeft); true }
        } else {
            { host.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_LEFT) }
        }
        // Reported before sending: a press that fails or never returns may still have reached the window.
        val dpadCenter: suspend () -> Boolean = if (canInjectInput) {
            { onCenter(); inputInjector.inject(InputInjector.Event.DpadCenter); true }
        } else {
            { onCenter(); host.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
        }

        val maxCycles = 4
        val stepsPerCycle = 2
        val stepDelayMs = 120L
        val stallThreshold = 2
        val anchorId = ENTITY_HEADER_CONTENT_ID
        var anchorHits = 0
        var totalRightSteps = 0
        var hadBootstrapInputFocus = false
        val isWindowAtRest = windowAtRestCheck()
        val awaitWindowAtRest: suspend () -> Boolean = {
            waitForLayoutStability(
                anchorId = anchorId,
                maxChecks = LAYOUT_REST_CHECKS,
                delayMs = LAYOUT_REST_DELAY_MS,
                isWindowAtRest = isWindowAtRest,
            ) != null
        }

        val (atRest, cacheBaseline) = coroutineScope {
            val baselineRead = async { cacheStats.read(BASELINE_READ_TIMEOUT_MS) }
            val settled = awaitWindowAtRest()
            settled to baselineRead.await()
        }
        when (cacheBaseline) {
            CacheStats.Zero -> {
                log(tag, INFO) { "CacheStats[baseline]: cache is empty, nothing to clear" }
                throw StepAbortException("Cache is already empty for ${target.installId}", treatAsSuccess = true)
            }

            is CacheStats.Positive -> log(tag, INFO) {
                "CacheStats[baseline]: baseline=${cacheBaseline.bytes}, presses are verified by cache bytes"
            }

            CacheStats.Unavailable -> log(tag, INFO) {
                "CacheStats[baseline]: unavailable, presses are verified by the storage page"
            }
        }
        if (!atRest) {
            log(tag, WARN) { "DPAD aborted before any key, the storage page never came to rest" }
            return false
        }

        if (preClickCheck != null && !preClickCheck()) {
            log(tag, WARN) { "DPAD aborted before any key, pre-click check no longer passes" }
            return null
        }

        val sizeParser = runCatching { SizeParser(host.service) }.getOrNull()
        val preRoot = host.windowRoot()
        val preSnapshot = takeStorageSnapshot(sizeParser, preRoot)

        val stats = (cacheBaseline as? CacheStats.Positive)?.let { baseline ->
            val cacheRow = if (sizeParser != null && preRoot != null) {
                readCacheRowSize(cacheSizeLabels, sizeParser, preRoot)
                    ?.takeIf { it > 0 }
                    ?.let { CacheRowBaseline(cacheSizeLabels, sizeParser, it) }
            } else {
                null
            }
            log(tag, INFO) {
                if (cacheRow != null) {
                    "CacheRow[baseline]: ${cacheRow.bytes}, verifies presses alongside the cache bytes"
                } else {
                    "CacheRow[baseline]: no Cache row with a size, only the cache bytes verify presses"
                }
            }
            StatsMode(cacheStats, baseline, cacheRow).also(onStatsMode)
        }

        // Motorola edge 70, Android 17 (#2843): input focus reaches the anchor ~150ms after
        // ACTION_FOCUS returns true. A DPAD key sent before that starts from the app bar's up button,
        // and DOWN then only reaches the anchor.
        suspend fun awaitAnchorInputFocus(reason: String, polls: Int) {
            var input: ACSNodeInfo? = null
            for (poll in 0..polls) {
                input = host.windowRoot()?.findFocus(ACSNodeInfo.FOCUS_INPUT)
                if (input?.packageName?.toString() == SETTINGS_PKG.name && input.viewIdResourceName == anchorId) {
                    log(tag, INFO) {
                        "DPAD bootstrap focus ($reason): input focus on anchor after ${BOOTSTRAP_FOCUS_MIN_WAIT_MS + poll * BOOTSTRAP_FOCUS_POLL_DELAY_MS}ms"
                    }
                    return
                }
                if (poll < polls) delay(BOOTSTRAP_FOCUS_POLL_DELAY_MS)
            }
            log(tag, INFO) {
                "DPAD bootstrap focus ($reason): input focus not on anchor after ${BOOTSTRAP_FOCUS_MIN_WAIT_MS + polls * BOOTSTRAP_FOCUS_POLL_DELAY_MS}ms (input=$input)"
            }
        }

        suspend fun bootstrapAnchor(reason: String, focusPolls: Int = 0): Boolean {
            val anchorNode = host.windowRoot()?.crawl()?.map { it.node }
                ?.firstOrNull { it.viewIdResourceName == anchorId }
            if (anchorNode == null) {
                log(tag, WARN) { "DPAD bootstrap skipped ($reason), anchor not found: $anchorId" }
                return false
            }

            val bootstrapInputFocusResult = anchorNode.performAction(ACSNodeInfo.ACTION_FOCUS)
            val bootstrapA11yFocusResult = anchorNode.performAction(ACSNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
            val alreadyInputFocused = anchorNode.isFocused
            val alreadyA11yFocused = anchorNode.isAccessibilityFocused
            hadBootstrapInputFocus = hadBootstrapInputFocus || bootstrapInputFocusResult || alreadyInputFocused
            log(tag) {
                "DPAD bootstrap focus ($reason): inputFocus=$bootstrapInputFocusResult, a11yFocus=$bootstrapA11yFocusResult, alreadyInputFocused=$alreadyInputFocused, alreadyA11yFocused=$alreadyA11yFocused, node=$anchorNode"
            }
            delay(BOOTSTRAP_FOCUS_MIN_WAIT_MS)
            if (focusPolls > 0) awaitAnchorInputFocus(reason, focusPolls)
            return bootstrapInputFocusResult || bootstrapA11yFocusResult || alreadyInputFocused || alreadyA11yFocused
        }

        // Best-effort reading of where focus sits after a movement. Treat it as a hint, not proof:
        // the framework often does not report focus after a DPAD action, so this can show the
        // pre-movement node even when the movement worked. Observed on a Pixel 8 that clears the
        // cache successfully - DPAD_DOWN returns true and focus still reads as the anchor. Only a
        // reading that differs from the anchor says anything; an unchanged one is inconclusive.
        suspend fun logFocus(step: String) {
            val focus = findFocusedNode()
            log(tag) {
                "DPAD focus after $step: input=${focus.inputFocused}, a11y=${focus.accessibilityFocused}"
            }
        }

        // Vetoes a blind DPAD_CENTER only on a reading that places focus outside the button row.
        // Missing focus or focus on the anchor is inconclusive (see above) and keeps the press.
        suspend fun focusVetoesCenter(source: String): Boolean {
            val root = host.windowRoot()
            val anchor = root?.crawl()?.map { it.node }?.firstOrNull { it.viewIdResourceName == anchorId }
            val input = root?.findFocus(ACSNodeInfo.FOCUS_INPUT)
            val a11y = root?.findFocus(ACSNodeInfo.FOCUS_ACCESSIBILITY)
            val focused = input ?: a11y
            val veto = when {
                root == null || anchor == null -> true
                focused == null -> false
                else -> focused.isOutsideButtonRow(anchor)
            }
            log(tag, INFO) {
                "DPAD focus guard ($source): root=${root != null}, anchor=$anchor, input=$input, a11y=$a11y, veto=$veto"
            }
            return veto
        }

        run {
            val fastBootstrapped = bootstrapAnchor("quick-try", focusPolls = BOOTSTRAP_FOCUS_POLLS)
            if (fastBootstrapped) {
                var centerSent = false
                val steppedDown = dpadDown()
                log(tag, INFO) { "DPAD_DOWN result=$steppedDown (source=quick-try)" }
                logFocus("quick-try DOWN")
                delay(stepDelayMs)
                val moved = dpadRight()
                log(tag, INFO) { "DPAD_RIGHT result=$moved (source=quick-try)" }
                if (!moved) {
                    log(tag, WARN) { "DPAD_RIGHT failed (source=quick-try)" }
                    logFocus("quick-try failed RIGHT")
                }
                if (moved) {
                    delay(stepDelayMs)
                }
                val positioned = if (moved && threeButtonRow) {
                    // GrapheneOS three-button row, RIGHT is "Manage storage": https://github.com/d4rken-org/sdmaid-se/issues/2842
                    val movedLeft = dpadLeft()
                    log(tag, INFO) { "DPAD_LEFT result=$movedLeft (source=quick-try)" }
                    if (movedLeft) {
                        delay(stepDelayMs)
                    } else {
                        log(tag, WARN) { "DPAD_LEFT failed (source=quick-try)" }
                        logFocus("quick-try failed LEFT")
                    }
                    movedLeft
                } else {
                    moved
                }
                if (positioned) {
                    if (focusVetoesCenter("quick-try")) {
                        log(tag, WARN) { "DPAD_CENTER skipped, focus outside the button row (source=quick-try)" }
                    } else {
                        val clicked = if (Bugs.isDryRun) true else dpadCenter().also { centerSent = it }
                        log(tag, INFO) { "DPAD_CENTER result=$clicked (source=quick-try)" }
                        val validated = clicked && validateWithDelta(
                            source = "quick-try",
                            preSnapshot = preSnapshot,
                            sizeParser = sizeParser,
                            timeoutMs = 800,
                            stats = stats,
                            statsPolls = STATS_LONG_POLLS,
                        )
                        if (validated) return true
                    }
                }
                // Check if anchor is still present before falling through to cycle loop.
                // If anchor is gone, the click likely had an effect (UI changed) — don't double-click.
                val anchorStillPresent = host.windowRoot()?.crawl()?.map { it.node }
                    ?.any { it.viewIdResourceName == anchorId } == true
                if (!anchorStillPresent) {
                    // GrapheneOS adds "Manage storage" for apps with a manageSpaceActivity, and
                    // DOWN+RIGHT lands on it: https://github.com/d4rken-org/sdmaid-se/issues/2842
                    var windowPkg: String? = null
                    var detour = false
                    if (centerSent) {
                        for (poll in 1..10) {
                            windowPkg = host.windowRoot()?.packageName?.toString()
                            detour = isManageSpaceDetour(centerSent, windowPkg, target.id.name)
                            if (detour) break
                            delay(100)
                        }
                        log(tag, INFO) {
                            "Quick-try: detour=$detour, rootPkg=$windowPkg, target=${target.id.name} (source=$MANAGE_SPACE_DETOUR_SOURCE)"
                        }
                    }
                    // Stock Settings has no third button, but its left button is "Manage space" for apps
                    // with a manageSpaceActivity, so a retry there would press it again.
                    if (detour && !settingsHasManageStorageText) {
                        log(tag, WARN) {
                            "Quick-try: detour without manage_storage_text, not retrying (source=$MANAGE_SPACE_DETOUR_SOURCE)"
                        }
                        return false
                    }
                    if (detour) {
                        val ops = ManageSpaceDetourOps(
                            back = { host.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) },
                            awaitAnchor = awaitWindowAtRest,
                            takeSnapshot = { takeStorageSnapshot(sizeParser, host.windowRoot()) },
                            bootstrapAnchor = { bootstrapAnchor(MANAGE_SPACE_DETOUR_SOURCE, focusPolls = BOOTSTRAP_FOCUS_POLLS) },
                            dpadDown = dpadDown,
                            dpadRight = dpadRight,
                            dpadLeft = dpadLeft,
                            focusVetoesCenter = { focusVetoesCenter(MANAGE_SPACE_DETOUR_SOURCE) },
                            dpadCenter = dpadCenter,
                            validate = { baseline ->
                                validateWithDelta(
                                    source = MANAGE_SPACE_DETOUR_SOURCE,
                                    preSnapshot = baseline,
                                    sizeParser = sizeParser,
                                    timeoutMs = 800,
                                    stats = stats,
                                )
                            },
                        )
                        return recoverFromManageSpaceDetour(tag, ops, preSnapshot, stepDelayMs)
                    }
                    log(tag, WARN) {
                        "Quick-try: validation failed but anchor gone, aborting to avoid double-click"
                    }
                    return false
                }
                log(tag, INFO) { "Quick-try failed, anchor still present, falling through to cycle loop" }
            }
        }

        for (cycle in 1..maxCycles) {
            val bootstrapped = bootstrapAnchor("cycle-$cycle")
            if (!bootstrapped) {
                delay(120)
                continue
            }

            var focusReady = false
            val maxFocusReadyChecks = 4
            for (i in 1..maxFocusReadyChecks) {
                val focus = findFocusedNode()
                val readyFocused = focus.inputFocused ?: focus.accessibilityFocused

                if (readyFocused?.viewIdResourceName == anchorId) {
                    focusReady = true
                    log(tag) { "Focus ready on anchor in cycle $cycle after $i checks: $readyFocused" }
                    break
                }

                if (readyFocused != null) {
                    log(tag) {
                        "Cycle $cycle focus on wrong node ($i/$maxFocusReadyChecks): $readyFocused, re-bootstrapping..."
                    }
                    bootstrapAnchor("cycle-$cycle-focus-wrong")
                } else if (i < maxFocusReadyChecks) {
                    delay(120)
                }
            }
            if (!focusReady) {
                log(tag, INFO) { "Focus not on anchor in cycle $cycle, attempting DPAD traversal anyway" }
            }

            val steppedDown = dpadDown()
            log(tag, INFO) { "DPAD_DOWN result=$steppedDown (cycle=$cycle)" }
            logFocus("cycle-$cycle DOWN")
            delay(stepDelayMs)

            var stalledSteps = 0
            var lastSignature: String? = null

            for (stepIdx in 1..stepsPerCycle) {
                val moved = dpadRight()
                if (!moved) {
                    log(tag, WARN) { "DPAD_RIGHT cycle=$cycle step=$stepIdx failed" }
                    logFocus("cycle-$cycle failed RIGHT")
                    return false
                }
                totalRightSteps++
                delay(stepDelayMs)

                val rootPkg = host.windowRoot()?.packageName
                val activeWindowPkg = runCatching {
                    host.service.windows.firstOrNull { it.isActive }?.root?.packageName
                }.getOrNull()
                val focus = findFocusedNode()
                val focused = focus.inputFocused ?: focus.accessibilityFocused

                log(tag) {
                    "DPAD cycle=$cycle step=$stepIdx globalStep=$totalRightSteps: rootPkg=$rootPkg, activeWindowPkg=$activeWindowPkg, focusedInput=${focus.inputFocused}, focusedAccessibility=${focus.accessibilityFocused}"
                }

                if (focused != null) {
                    val matchesLabel = labels.any { label ->
                        focused.textMatches(label) || focused.contentDescMatches(label)
                    }
                    if (matchesLabel) {
                        log(tag, INFO) { "Found Clear cache via focus: $focused" }
                        val clicked = if (Bugs.isDryRun) true else dpadCenter()
                        log(tag, INFO) { "DPAD_CENTER result=$clicked (source=label-match)" }
                        if (!clicked) return false
                        val validated = validateWithDelta(
                            source = "label-match",
                            preSnapshot = preSnapshot,
                            sizeParser = sizeParser,
                            stats = stats,
                            statsPolls = STATS_LONG_POLLS,
                        )
                        if (validated) return true
                        break // Delta validation failed, try next cycle
                    }

                    if (focused.viewIdResourceName == anchorId) {
                        anchorHits++
                        log(tag, INFO) { "entity_header_content hit #$anchorHits (cycle=$cycle step=$stepIdx)" }
                    }
                }

                val signature =
                    focused?.let { "${it.viewIdResourceName}|${it.className}|${it.text}|${it.contentDescription}" }
                stalledSteps = if (signature == null || signature == lastSignature) stalledSteps + 1 else 0
                lastSignature = signature

                if (stalledSteps >= stallThreshold) {
                    log(tag, INFO) {
                        "DPAD focus stalled in cycle $cycle after $stepIdx steps (stalledSteps=$stalledSteps), re-bootstrapping"
                    }
                    break
                }
            }
        }

        if (hadBootstrapInputFocus) {
            val maxBlindPositions = 4
            val startPosition = 2 // quick-try already covered position 1
            log(tag, INFO) {
                "Blind sweep: trying positions $startPosition..$maxBlindPositions (totalRightSteps=$totalRightSteps, anchorHits=$anchorHits)"
            }

            for (rightSteps in startPosition..maxBlindPositions) {
                if (!bootstrapAnchor("blind-sweep-$rightSteps", focusPolls = BOOTSTRAP_FOCUS_SHORT_POLLS)) {
                    log(tag, WARN) { "Blind sweep: anchor lost at position $rightSteps, aborting" }
                    return false
                }

                val steppedDown = dpadDown()
                log(tag, INFO) { "DPAD_DOWN result=$steppedDown (source=blind-sweep-$rightSteps)" }
                delay(stepDelayMs)
                repeat(rightSteps) { step ->
                    val moved = dpadRight()
                    if (!moved) log(tag, WARN) { "DPAD_RIGHT blind-sweep-$rightSteps step=${step + 1} failed" }
                    delay(stepDelayMs)
                }
                if (focusVetoesCenter("blind-sweep-$rightSteps")) {
                    log(tag, WARN) { "DPAD_CENTER skipped, focus outside the button row (source=blind-sweep-$rightSteps)" }
                    continue
                }

                val clicked = if (Bugs.isDryRun) true else dpadCenter()
                log(tag, INFO) { "DPAD_CENTER result=$clicked (source=blind-sweep-$rightSteps)" }
                if (!clicked) continue

                if (validateWithDelta("blind-sweep-$rightSteps", preSnapshot, sizeParser, timeoutMs = 800, stats = stats)) {
                    return true
                }

                val anchorStillPresent = host.windowRoot()?.crawl()?.map { it.node }
                    ?.any { it.viewIdResourceName == anchorId } == true
                if (!anchorStillPresent) {
                    log(tag, WARN) { "Blind sweep: anchor gone after position $rightSteps, aborting" }
                    return false
                }
            }
        }

        log(
            tag,
            WARN
        ) { "Could not find Clear cache after $totalRightSteps DPAD steps (no successful anchor bootstrap)" }
        return false
    }

    private val mainPlan: suspend AutomationExplorer.Context.(Installed) -> Unit = plan@{ pkg ->
        log(TAG, INFO) { "Executing plan for ${pkg.installId} with context $this" }

        run {
            val storageEntryLabels =
                aospLabels.getStorageEntryDynamic(this) + aospLabels.getStorageEntryStatic(this)
            log(TAG) { "storageEntryLabels=${storageEntryLabels.toVisualStrings()}" }

            val computingLabels = aospLabels.getComputingSizeDynamic(this)
            log(TAG) { "computingLabels=${computingLabels.toVisualStrings()}" }

            val storageFinder = storageEntryFinder.storageFinderAOSP(storageEntryLabels, pkg)

            val step = AutomationStep(
                source = tag,
                descriptionInternal = "Storage entry",
                label = R.string.appcleaner_automation_progress_find_storage.toCaString(storageEntryLabels),
                windowLaunch = windowLauncherDefaultSettings(pkg),
                windowCheck = windowCheckDefaultSettings(SETTINGS_PKG, ipcFunnel, pkg),
                nodeRecovery = defaultNodeRecovery(pkg, extraBusyLabels = computingLabels),
                nodeAction = defaultFindAndClick(finder = storageFinder),
            )
            stepper.withProgress(this) { process(this@plan, step) }
        }

        run {
            val clearCacheButtonLabels =
                aospLabels.getClearCacheDynamic(this) + aospLabels.getClearCacheStatic(this)
            log(TAG) { "clearCacheButtonLabels=${clearCacheButtonLabels.toVisualStrings()}" }

            val cacheSizeLabels =
                aospLabels.getCacheSizeLabelDynamic(this) + aospLabels.getCacheSizeLabelStatic(this)
            log(TAG) { "cacheSizeLabels=${cacheSizeLabels.toVisualStrings()}" }

            val canInjectInput = inputInjector.canInject()
            log(TAG, INFO) { "InputInjector available? (canInjectInput=$canInjectInput)" }

            // On Android 16+, "Clear cache" and "Clear storage" buttons are marked as NAF
            // (Not Accessibility Focusable) on some devices, making them invisible to the
            // accessibility service. DPAD navigation works around this by using keyboard-style
            // navigation (DOWN, RIGHT, CENTER) from the entity_header_content anchor to blindly
            // click the invisible button. Two routes open it: on 36+ the manufacturers in
            // DPAD_FALLBACK_MANUFACTURERS, on 37+ any other device whose strip below the app header
            // is empty (hasEmptyButtonBand) on two size checks in a row and again right before the
            // first key.
            // https://github.com/d4rken-org/sdmaid-se/issues/2056
            val dpadManufacturer = supportsDpadFallback(BuildWrap.MANUFACTOR)
            val allowlistedDpad = hasApiLevel(36) && dpadManufacturer
            val bandCheckEligible = !allowlistedDpad && hasApiLevel(37)
            log(TAG, INFO) {
                "dpadManufacturer=$dpadManufacturer, allowlistedDpad=$allowlistedDpad, bandCheckEligible=$bandCheckEligible (MANUFACTURER=${BuildWrap.MANUFACTOR}, PRODUCT=${BuildWrap.PRODUCT})"
            }

            val settingsHasManageStorageText = try {
                @SuppressLint("DiscouragedApi")
                val id = host.service.packageManager.getResourcesForApplication(SETTINGS_PKG.name)
                    .getIdentifier("manage_storage_text", "string", SETTINGS_PKG.name)
                id != 0
            } catch (e: Exception) {
                log(TAG, WARN) { "Failed to look up manage_storage_text: ${e.asLog()}" }
                false
            }
            val manageSpaceActivityName = try {
                pkg.applicationInfo?.manageSpaceActivityName
            } catch (e: Exception) {
                log(TAG, WARN) { "Failed to read manageSpaceActivityName: ${e.asLog()}" }
                null
            }
            val manageSpaceActivityResolves = manageSpaceActivityName != null && try {
                val intent = Intent(Intent.ACTION_DEFAULT).setClassName(pkg.id.name, manageSpaceActivityName)
                host.service.packageManager.resolveActivity(intent, 0) != null
            } catch (e: Exception) {
                log(TAG, WARN) { "Failed to resolve $manageSpaceActivityName: ${e.asLog()}" }
                false
            }
            val threeButtonRow = predictsManageStorageButton(
                settingsHasManageStorageText,
                manageSpaceActivityName,
                manageSpaceActivityResolves,
            )
            log(TAG, INFO) {
                "threeButtonRow=$threeButtonRow (manageStorageText=$settingsHasManageStorageText, manageSpaceActivity=$manageSpaceActivityName, resolves=$manageSpaceActivityResolves)"
            }

            val bandMinGapPx = if (bandCheckEligible) {
                val density = host.service.resources.displayMetrics.density
                if (density > 0f) {
                    ceil(BUTTON_BAND_MIN_GAP_DP * density).toInt()
                } else {
                    log(TAG, WARN) { "Unknown display density ($density), button band check can't qualify" }
                    0
                }
            } else {
                0
            }

            var nodeActionAttempts = 0
            var dpadExhausted = false
            var bandStreak = 0
            var bandConfirmed = false
            var sizeParser: SizeParser? = null
            var pendingVerdict = false
            val verdictConfirmer = VerdictConfirmer()
            // Outlives single DPAD runs and step attempts, so a read that never returned is never doubled.
            val cacheStats = CacheStatsReader(host.scope, TAG) {
                inaccessibleCacheProvider.determineCache(pkg)?.totalSize
            }
            // Replaying the plan would press the same blind positions again.
            fun dpadExhaustedAbort(attempt: Int) = StepAbortException(
                "DPAD exhausted, clear-cache button not reachable (attempt=$attempt)",
                replayable = false,
            )
            val nodeAction: suspend StepContext.() -> Boolean = action@{
                val attempt = nodeActionAttempts++
                val candidate = findClearCacheCandidate(clearCacheButtonLabels)

                if (candidate != null) {
                    val target = resolveClickTarget(candidate)
                    if (target != null) {
                        log(tag) { "Clicking Clear cache target: $target" }
                        return@action clickClearCache(isDryRun = Bugs.isDryRun, pkg = pkg, node = target)
                    }
                    log(tag, WARN) { "Could not resolve clickable target from: $candidate" }
                }

                // No clickable "Clear cache" anywhere. Settings renders the cache size on this very
                // screen though, so read it instead of polling for a button that may not exist:
                // an already empty cache leaves the button greyed out, or - as on Hello UI /
                // Android 17 - collapsed to zero height with no text and no content-desc, in which
                // case findClearCacheCandidate can never match it and we would just run out the
                // step timeout. Unlike the DPAD fallback below this only reads the screen, never
                // blind-clicks, so it is safe on every ROM.
                // Both crawls are expensive and this loop runs ~10x/s, so don't do it every pass.
                // A terminal verdict needs two of these in a row, so it lands after ~1.2s.
                val sizeCheckDue = attempt >= CACHE_SIZE_CHECK_MIN_ATTEMPTS &&
                    (attempt - CACHE_SIZE_CHECK_MIN_ATTEMPTS) % CACHE_SIZE_CHECK_INTERVAL == 0

                // Crawls the window as well, so it shares the size check's cadence. Runs before the
                // verdict so that this pass's verdict and DPAD branch already see a confirmation.
                if (sizeCheckDue && bandCheckEligible && !bandConfirmed) {
                    val qualifies = bandMinGapPx > 0 &&
                        host.windowRoot()?.hasEmptyButtonBand(bandMinGapPx) == true
                    bandStreak = if (qualifies) bandStreak + 1 else 0
                    if (bandStreak >= BUTTON_BAND_CONFIRMATIONS) {
                        bandConfirmed = true
                        log(tag, INFO) { "Empty button band confirmed, DPAD fallback enabled (attempt=$attempt)" }
                    } else {
                        log(tag) { "Button band qualifies=$qualifies, streak=$bandStreak (attempt=$attempt)" }
                    }
                }
                val useDpadFallback = allowlistedDpad || bandConfirmed

                if (sizeCheckDue) {
                    if (sizeParser == null) sizeParser = runCatching { SizeParser(host.service) }.getOrNull()
                    val cacheSize = readCacheRowSize(cacheSizeLabels, sizeParser)
                    val verdict = noButtonVerdict(cacheSize, pkg.isSystemApp, useDpadFallback)
                    // A verdict that still needs its confirming pass must not be raced by a blind
                    // click: an already empty cache would otherwise be reported as a failure.
                    pendingVerdict = verdict != NoButtonVerdict.KEEP_TRYING
                    when (verdictConfirmer.confirm(verdict)) {
                        NoButtonVerdict.KEEP_TRYING -> {
                            log(tag, VERBOSE) { "$verdict, cacheSize=$cacheSize (attempt=$attempt)" }
                        }

                        NoButtonVerdict.ALREADY_EMPTY -> {
                            log(tag, INFO) { "Settings reports an empty cache, nothing left to clear" }
                            throw StepAbortException(
                                "Cache is already empty for ${pkg.installId}",
                                treatAsSuccess = true,
                            )
                        }

                        NoButtonVerdict.NO_BUTTON -> {
                            log(tag, WARN) { "No clear cache button, but $cacheSize bytes of cache" }
                            throw LockedAppCacheException("No clear cache button: ${pkg.installId}")
                        }
                    }
                }

                // DPAD fallback: try text search a few times first for AOSP ROMs with accessible buttons,
                // then fall back to DPAD navigation. tryClickViaFocusNavigation calls waitForLayoutStability
                // internally, so we don't need many retries here to wait for the screen to load.
                val dpadMinAttempts = 2
                if (useDpadFallback && dpadExhausted) {
                    throw dpadExhaustedAbort(attempt)
                } else if (useDpadFallback && attempt >= dpadMinAttempts && !pendingVerdict) {
                    log(tag, INFO) { "DPAD fallback triggered (attempt=$attempt)" }
                    val preClickCheck: (suspend () -> Boolean)? = if (allowlistedDpad) {
                        null
                    } else {
                        suspend { host.windowRoot()?.hasEmptyButtonBand(bandMinGapPx) == true }
                    }
                    when (
                        tryClickViaFocusNavigation(
                            clearCacheButtonLabels,
                            pkg,
                            cacheStats,
                            canInjectInput,
                            threeButtonRow,
                            settingsHasManageStorageText,
                            cacheSizeLabels,
                            preClickCheck,
                        )
                    ) {
                        true -> return@action true
                        false -> {
                            dpadExhausted = true
                            log(tag, WARN) { "DPAD fallback exhausted, won't retry" }
                            throw dpadExhaustedAbort(attempt)
                        }

                        null -> {
                            bandConfirmed = false
                            bandStreak = 0
                            log(tag, WARN) { "Button band changed before the first key, back to observing" }
                        }
                    }
                } else if (useDpadFallback) {
                    log(tag) { "Skipping DPAD fallback (attempt=$attempt, pendingVerdict=$pendingVerdict)" }
                }

                false
            }

            val step = AutomationStep(
                source = tag,
                descriptionInternal = "Clear cache button",
                label = R.string.appcleaner_automation_progress_find_clear_cache.toCaString(clearCacheButtonLabels),
                windowCheck = windowCheckDefaultSettings(SETTINGS_PKG, ipcFunnel, pkg),
                nodeAction = nodeAction,
            )
            stepper.withProgress(this) { process(this@plan, step) }
        }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: AOSPSpecs): AppCleanerSpecGenerator
    }

    companion object {
        val SETTINGS_PKG = "com.android.settings".toPkgId()

        // Give the button a couple of passes to show up before we start reading sizes, and then
        // only read about once per second: nodeAction is retried every 100ms and each check
        // crawls the whole window.
        private const val CACHE_SIZE_CHECK_MIN_ATTEMPTS = 2
        private const val CACHE_SIZE_CHECK_INTERVAL = 10

        // Taken on size check passes, so two in a row span about a second.
        private const val BUTTON_BAND_CONFIRMATIONS = 2
        private const val BUTTON_BAND_MIN_GAP_DP = 48

        // The DPAD run plus containment (two polls of up to 1s) and the final cache read has to fit
        // into one step attempt. The reserve keeps room for the latter two.
        private const val DPAD_RUN_DEADLINE_MS = 10_000L
        private const val POST_RUN_RESERVE_MS = 3_500L
        private const val CONTAINMENT_POLLS = 10
        private const val CONTAINMENT_POLL_DELAY_MS = 100L

        // How long after the focus actions input focus gets to reach the anchor: up to 600ms before the
        // first key and the detour retry, 250ms before a blind-sweep key. The cycle loop checks focus
        // on the anchor itself.
        private const val BOOTSTRAP_FOCUS_MIN_WAIT_MS = 100L
        private const val BOOTSTRAP_FOCUS_POLL_DELAY_MS = 50L
        private const val BOOTSTRAP_FOCUS_POLLS = 10
        private const val BOOTSTRAP_FOCUS_SHORT_POLLS = 3

        // Up to about 3s for Settings' entry transition to end.
        private const val LAYOUT_REST_CHECKS = 15
        private const val LAYOUT_REST_DELAY_MS = 200L

        private const val BASELINE_READ_TIMEOUT_MS = 2_000L
        private const val FINAL_RECHECK_READ_TIMEOUT_MS = 1_000L

        // StorageStats lag a clear: two reads 500ms apart can both still show the old size. The long
        // window is for the presses most likely to hit "Clear cache".
        private const val STATS_POLL_DELAY_MS = 250L
        private const val STATS_POLL_READ_TIMEOUT_MS = 250L
        private const val STATS_LONG_POLLS = 16
        private const val STATS_SHORT_POLLS = 3

        private val TAG: String = logTag("AppCleaner", "Automation", "AOSP", "Specs")
    }

}
