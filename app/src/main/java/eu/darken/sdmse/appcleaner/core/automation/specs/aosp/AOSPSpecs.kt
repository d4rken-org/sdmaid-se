package eu.darken.sdmse.appcleaner.core.automation.specs.aosp

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
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

    private suspend fun tryKeyboardNavigation(): Boolean {
        log(tag, INFO) { "Trying keyboard navigation (API 36+ fallback)" }
        // Navigate right 3 times to reach Clear cache button, then click
        // Layout: [App icon] [Clear data] [Clear cache]
        log(tag, INFO) { "Sending DPAD_RIGHT x3 + DPAD_CENTER" }
        inputInjector.inject(
            InputInjector.Event.DpadRight,
            InputInjector.Event.DpadRight,
            InputInjector.Event.DpadRight,
            InputInjector.Event.DpadCenter,
        )

        return true
    }

    private suspend fun StepContext.tryAccessibilityDpadNavigation(labels: Collection<String>): Boolean {
        log(tag, INFO) { "Trying accessibility DPAD navigation (anchor-based)" }

        if (!hasApiLevel(33)) {
            log(tag, WARN) { "DPAD global actions require API 33+" }
            return false
        }

        val service = host.service
        val maxAttempts = 10
        val anchorId = "com.android.settings:id/entity_header_content"
        var anchorHits = 0

        waitForLayoutStability(anchorId)

        val anchorNode = host.windowRoot()?.crawl()?.map { it.node }
            ?.firstOrNull { it.viewIdResourceName == anchorId }
        var bootstrapInputFocusResult = false
        var bootstrapA11yFocusResult = false
        if (anchorNode != null) {
            bootstrapInputFocusResult = anchorNode.performAction(ACSNodeInfo.ACTION_FOCUS)
            bootstrapA11yFocusResult = anchorNode.performAction(ACSNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
            log(tag, INFO) {
                "DPAD bootstrap focus on anchor: inputFocus=$bootstrapInputFocusResult, a11yFocus=$bootstrapA11yFocusResult, node=$anchorNode"
            }
            delay(100)
        } else {
            log(tag, WARN) { "DPAD bootstrap focus skipped, anchor not found: $anchorId" }
        }

        var focusReady = false
        val maxFocusReadyChecks = 6
        for (i in 1..maxFocusReadyChecks) {
            val focus = findFocusedNode()
            val readyFocused = focus.inputFocused ?: focus.accessibilityFocused

            if (readyFocused != null) {
                if (readyFocused.viewIdResourceName == anchorId) {
                    focusReady = true
                    log(tag, INFO) { "Focus ready on anchor after $i checks: $readyFocused" }
                    break
                } else {
                    log(tag, INFO) {
                        "Focus on wrong node ($i/$maxFocusReadyChecks): $readyFocused, re-bootstrapping..."
                    }
                    host.windowRoot()?.crawl()?.map { it.node }
                        ?.firstOrNull { it.viewIdResourceName == anchorId }
                        ?.performAction(ACSNodeInfo.ACTION_FOCUS)
                    delay(150)
                }
            } else {
                delay(120)
            }
        }
        if (!focusReady) {
            log(tag, INFO) { "Focus not on anchor after bootstrap, continuing with DPAD traversal" }
        }

        repeat(maxAttempts) { idx ->
            @SuppressLint("InlinedApi")
            val moved = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT)
            if (!moved) {
                log(tag, WARN) { "GLOBAL_ACTION_DPAD_RIGHT #${idx + 1} failed" }
                return false
            }
            delay(80)

            val rootPkg = host.windowRoot()?.packageName
            val activeWindowPkg = runCatching {
                service.windows.firstOrNull { it.isActive }?.root?.packageName
            }.getOrNull()
            val focus = findFocusedNode()
            val focused = focus.inputFocused ?: focus.accessibilityFocused

            log(tag, INFO) {
                "DPAD step #${idx + 1}: rootPkg=$rootPkg, activeWindowPkg=$activeWindowPkg, focusedInput=${focus.inputFocused}, focusedAccessibility=${focus.accessibilityFocused}"
            }

            if (focused != null) {
                // Check if it matches Clear cache directly (future-proofing)
                val matchesLabel = labels.any { label ->
                    focused.textMatches(label) || focused.contentDescMatches(label)
                }
                if (matchesLabel) {
                    log(tag, INFO) { "Found Clear cache via focus: $focused" }
                    @SuppressLint("InlinedApi")
                    val clicked = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_CENTER)
                    delay(120)
                    return clicked
                }

                if (focused.viewIdResourceName == anchorId) {
                    anchorHits++
                    log(tag, INFO) { "entity_header_content hit #$anchorHits" }
                    // 1st hit: header area, 2nd hit: Clear cache button (child of entity_header_content)
                    if (anchorHits >= 2) {
                        log(tag, INFO) { "Pressing DPAD_CENTER on presumed Clear cache (anchor hit #$anchorHits)" }
                        val clicked = if (Bugs.isDryRun) {
                            true
                        } else {
                            @SuppressLint("InlinedApi")
                            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_CENTER)
                        }
                        delay(120)
                        return clicked
                    }
                }
            }
        }

        if (anchorHits < 2 && anchorNode != null && bootstrapInputFocusResult) {
            log(tag, WARN) {
                "Insufficient anchor progress (anchorHits=$anchorHits); using guarded blind anchor sequence (RIGHT x2 + CENTER)"
            }

            repeat(2) { idx ->
                @SuppressLint("InlinedApi")
                val moved = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT)
                log(tag, INFO) { "Blind DPAD_RIGHT #${idx + 1} result=$moved" }
                if (!moved) return false
                delay(80)
            }

            val clicked = if (Bugs.isDryRun) {
                true
            } else {
                @SuppressLint("InlinedApi")
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_CENTER)
            }
            log(tag, INFO) { "Blind DPAD_CENTER result=$clicked" }
            delay(120)
            return clicked
        }

        log(tag, WARN) { "Could not find Clear cache after $maxAttempts DPAD steps" }
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
            log(TAG, INFO) { "InputInjector available? canInjectInput=$canInjectInput)" }

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

                val isGoogle = BuildWrap.MANUFACTOR.equals("Google", ignoreCase = true)
                val isBetaBuild = BuildWrap.PRODUCT?.contains("_beta", ignoreCase = true) == true
                val useA16DpadWorkaround = hasApiLevel(36) && isGoogle && isBetaBuild
                log(tag, INFO) {
                    "isGoogle=$isGoogle, isBetaBuild=$isBetaBuild, useA16DpadWorkaround=$useA16DpadWorkaround (MANUFACTURER=${BuildWrap.MANUFACTOR}, PRODUCT=${BuildWrap.PRODUCT})"
                }
                if (useA16DpadWorkaround) {
                    if (tryAccessibilityDpadNavigation(clearCacheButtonLabels)) return@action true
                }

                if (hasApiLevel(36) && canInjectInput) {
                    return@action tryKeyboardNavigation()
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
