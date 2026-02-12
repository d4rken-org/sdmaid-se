package eu.darken.sdmse.appcleaner.core.automation.specs.aosp

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.view.accessibility.AccessibilityEvent
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.core.automation.specs.AppCleanerSpecGenerator
import eu.darken.sdmse.appcleaner.core.automation.specs.StorageEntryFinder
import eu.darken.sdmse.appcleaner.core.automation.specs.clickClearCache
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
import eu.darken.sdmse.automation.core.input.InputInjector
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.automation.core.specs.AutomationSpec
import eu.darken.sdmse.automation.core.specs.defaultFindAndClick
import eu.darken.sdmse.automation.core.specs.defaultNodeRecovery
import eu.darken.sdmse.automation.core.specs.windowCheckDefaultSettings
import eu.darken.sdmse.automation.core.specs.windowLauncherDefaultSettings
import eu.darken.sdmse.common.BuildWrap
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.debug.toVisualStrings
import eu.darken.sdmse.common.device.DeviceDetective
import eu.darken.sdmse.common.device.RomType
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.progress.withProgress
import eu.darken.sdmse.main.core.GeneralSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@Reusable
class AOSPSpecs @Inject constructor(
    private val ipcFunnel: IPCFunnel,
    private val deviceDetective: DeviceDetective,
    private val aospLabels: AOSPLabels,
    private val storageEntryFinder: StorageEntryFinder,
    private val generalSettings: GeneralSettings,
    private val stepper: Stepper,
    private val inputInjector: InputInjector,
) : AppCleanerSpecGenerator {

    override val tag: String = TAG

    override suspend fun isResponsible(pkg: Installed): Boolean {
        val romType = generalSettings.romTypeDetection.value()
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

    @SuppressLint("InlinedApi")
    private suspend fun StepContext.tryClickViaFocusNavigation(
        labels: Collection<String>,
        canInjectInput: Boolean,
    ): Boolean {
        log(tag, INFO) { "Trying DPAD navigation (anchor-based, inputInjection=$canInjectInput)" }

        if (!canInjectInput && !hasApiLevel(33)) {
            log(tag, WARN) { "DPAD global actions require API 33+" }
            return false
        }

        val dpadRight: suspend () -> Boolean = if (canInjectInput) {
            { inputInjector.inject(InputInjector.Event.DpadRight); true }
        } else {
            { host.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT) }
        }
        val dpadCenter: suspend () -> Boolean = if (canInjectInput) {
            { inputInjector.inject(InputInjector.Event.DpadCenter); true }
        } else {
            { host.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
        }

        val maxCycles = 4
        val stepsPerCycle = 2
        val stepDelayMs = 120L
        val stallThreshold = 2
        val anchorId = "com.android.settings:id/entity_header_content"
        var anchorHits = 0
        var totalRightSteps = 0
        var hadBootstrapInputFocus = false

        waitForLayoutStability(anchorId)

        suspend fun bootstrapAnchor(reason: String): Boolean {
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
            delay(100)
            return bootstrapInputFocusResult || bootstrapA11yFocusResult || alreadyInputFocused || alreadyA11yFocused
        }

        if (canInjectInput) {
            val fastBootstrapped = bootstrapAnchor("input-fast-path")
            if (fastBootstrapped) {
                val moved = dpadRight()
                if (moved) {
                    delay(stepDelayMs)
                    val clicked = if (Bugs.isDryRun) true else dpadCenter()
                    log(tag, INFO) { "DPAD_CENTER result=$clicked (source=input-fast-path)" }
                    if (clicked && validateClickEffect("input-fast-path", timeoutMs = 800)) return true
                }
                // Check if anchor is still present before falling through to cycle loop.
                // If anchor is gone, the click likely had an effect (UI changed) — don't double-click.
                val anchorStillPresent = host.windowRoot()?.crawl()?.map { it.node }
                    ?.any { it.viewIdResourceName == anchorId } == true
                if (!anchorStillPresent) {
                    log(tag, WARN) {
                        "Input fast-path: validation failed but anchor gone, aborting to avoid double-click"
                    }
                    return false
                }
                log(tag, INFO) { "Input fast-path failed, anchor still present, falling through to cycle loop" }
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
                } else {
                    delay(120)
                }
            }
            if (!focusReady) {
                log(tag, INFO) { "Focus not on anchor in cycle $cycle, attempting DPAD traversal anyway" }
            }

            var stalledSteps = 0
            var lastSignature: String? = null

            for (stepIdx in 1..stepsPerCycle) {
                val moved = dpadRight()
                if (!moved) {
                    log(tag, WARN) { "DPAD_RIGHT cycle=$cycle step=$stepIdx failed" }
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
                        return if (clicked) validateClickEffect("label-match") else false
                    }

                    if (focused.viewIdResourceName == anchorId) {
                        anchorHits++
                        log(tag, INFO) { "entity_header_content hit #$anchorHits (cycle=$cycle step=$stepIdx)" }
                        if (anchorHits >= 2) {
                            log(tag, INFO) { "Pressing DPAD_CENTER on presumed Clear cache (anchor hit #$anchorHits)" }
                            val clicked = if (Bugs.isDryRun) true else dpadCenter()
                            log(tag, INFO) { "DPAD_CENTER result=$clicked (source=anchor-hit-$anchorHits)" }
                            return if (clicked) validateClickEffect("anchor-hit-$anchorHits") else false
                        }
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
            log(tag, WARN) {
                "Insufficient anchor progress after $totalRightSteps DPAD_RIGHT steps (anchorHits=$anchorHits); re-bootstrapping for blind fallback (RIGHT + CENTER)"
            }

            if (!bootstrapAnchor("blind-fallback")) {
                log(tag, WARN) { "Blind fallback: anchor lost, aborting" }
                return false
            }

            val moved = dpadRight()
            log(tag, INFO) { "Blind DPAD_RIGHT result=$moved" }
            if (!moved) return false
            delay(stepDelayMs)

            val clicked = if (Bugs.isDryRun) true else dpadCenter()
            log(tag, INFO) { "DPAD_CENTER result=$clicked (source=blind-fallback)" }
            return if (clicked) validateClickEffect("blind-fallback") else false
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

            val storageFinder = storageEntryFinder.storageFinderAOSP(storageEntryLabels, pkg)

            val step = AutomationStep(
                source = tag,
                descriptionInternal = "Storage entry",
                label = R.string.appcleaner_automation_progress_find_storage.toCaString(storageEntryLabels),
                windowLaunch = windowLauncherDefaultSettings(pkg),
                windowCheck = windowCheckDefaultSettings(SETTINGS_PKG, ipcFunnel, pkg),
                nodeRecovery = defaultNodeRecovery(pkg),
                nodeAction = defaultFindAndClick(finder = storageFinder),
            )
            stepper.withProgress(this) { process(this@plan, step) }
        }

        run {
            val clearCacheButtonLabels =
                aospLabels.getClearCacheDynamic(this) + aospLabels.getClearCacheStatic(this)
            log(TAG) { "clearCacheButtonLabels=${clearCacheButtonLabels.toVisualStrings()}" }

            val canInjectInput = inputInjector.canInject()
            log(TAG, INFO) { "InputInjector available? (canInjectInput=$canInjectInput)" }

            val nodeAction: suspend StepContext.() -> Boolean = action@{
                val candidate = findClearCacheCandidate(clearCacheButtonLabels)

                if (candidate != null) {
                    val target = resolveClickTarget(candidate)
                    if (target != null) {
                        log(tag) { "Clicking Clear cache target: $target" }
                        return@action clickClearCache(isDryRun = Bugs.isDryRun, pkg = pkg, node = target)
                    }
                    log(tag, WARN) { "Could not resolve clickable target from: $candidate" }
                }

                // https://github.com/d4rken-org/sdmaid-se/issues/2056
                val isGoogle = BuildWrap.MANUFACTOR.equals("Google", ignoreCase = true)
                val isBetaBuild = BuildWrap.PRODUCT?.contains("_beta", ignoreCase = true) == true
                val useA16DpadWorkaround = hasApiLevel(36) && isGoogle && isBetaBuild
                log(tag, INFO) {
                    "isGoogle=$isGoogle, isBetaBuild=$isBetaBuild, useA16DpadWorkaround=$useA16DpadWorkaround (MANUFACTURER=${BuildWrap.MANUFACTOR}, PRODUCT=${BuildWrap.PRODUCT})"
                }
                if (useA16DpadWorkaround) {
                    if (tryClickViaFocusNavigation(clearCacheButtonLabels, canInjectInput)) return@action true
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
        private val TAG: String = logTag("AppCleaner", "Automation", "AOSP", "Specs")
    }

}
