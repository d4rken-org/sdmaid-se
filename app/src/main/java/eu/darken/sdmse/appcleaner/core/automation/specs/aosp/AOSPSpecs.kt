package eu.darken.sdmse.appcleaner.core.automation.specs.aosp

import android.graphics.Rect
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
import eu.darken.sdmse.automation.core.common.isClickyButton
import eu.darken.sdmse.automation.core.common.isTextView
import eu.darken.sdmse.automation.core.common.stepper.AutomationStep
import eu.darken.sdmse.automation.core.common.stepper.StepContext
import eu.darken.sdmse.automation.core.common.stepper.Stepper
import eu.darken.sdmse.automation.core.common.stepper.clickGestureAtCoords
import eu.darken.sdmse.automation.core.common.stepper.findClickableParent
import eu.darken.sdmse.automation.core.common.stepper.findClickableSibling
import eu.darken.sdmse.automation.core.common.stepper.findNode
import eu.darken.sdmse.automation.core.common.stepper.findNodeByContentDesc
import eu.darken.sdmse.automation.core.common.stepper.findNodeByLabel
import eu.darken.sdmse.automation.core.input.InputInjector
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.automation.core.specs.AutomationSpec
import eu.darken.sdmse.automation.core.specs.defaultFindAndClick
import eu.darken.sdmse.automation.core.specs.defaultNodeRecovery
import eu.darken.sdmse.automation.core.specs.windowCheckDefaultSettings
import eu.darken.sdmse.automation.core.specs.windowLauncherDefaultSettings
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
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

    private suspend fun StepContext.tryCoordinateBasedClick(): Boolean {
        log(tag, INFO) { "Trying coordinate-based click (API 36+ fallback)" }

        val headerNode = findNode {
            it.viewIdResourceName?.contains("entity_header") == true
        }

        if (headerNode == null) {
            log(tag, WARN) { "No entity_header anchor found" }
            return false
        }

        val headerBounds = Rect().apply { headerNode.getBoundsInScreen(this) }
        log(tag, INFO) { "Found entity_header at: $headerBounds" }

        // Clear cache button: right side, below header
        val buttonX = headerBounds.left + (headerBounds.width() * 0.74f)
        val buttonY = headerBounds.bottom + 153f

        log(tag, INFO) { "Coordinate-based click at ($buttonX, $buttonY) - targeting Clear cache" }
        return clickGestureAtCoords(buttonX, buttonY, isDryRun = Bugs.isDryRun)
    }

    private suspend fun tryKeyboardNavigation(): Boolean {
        log(tag, INFO) { "Trying keyboard navigation (API 36+ fallback)" }

        if (!inputInjector.canInject()) {
            log(tag, WARN) { "Cannot inject input events - no ADB or Root access" }
            return false
        }

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

                if (hasApiLevel(36)) {
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
