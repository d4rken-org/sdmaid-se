package eu.darken.sdmse.appcleaner.core.automation.specs.aosp

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
import eu.darken.sdmse.automation.core.common.isClickyButton
import eu.darken.sdmse.automation.core.common.stepper.AutomationStep
import eu.darken.sdmse.automation.core.common.stepper.StepContext
import eu.darken.sdmse.automation.core.common.stepper.Stepper
import eu.darken.sdmse.automation.core.common.stepper.findClickableParent
import eu.darken.sdmse.automation.core.common.stepper.findClickableSibling
import eu.darken.sdmse.automation.core.common.stepper.findNodeByLabel
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.automation.core.common.stepper.clickGesture
import eu.darken.sdmse.automation.core.common.stepper.clickNormal
import eu.darken.sdmse.automation.core.specs.AutomationSpec
import eu.darken.sdmse.automation.core.specs.defaultNodeRecovery
import eu.darken.sdmse.automation.core.specs.windowCheckDefaultSettings
import eu.darken.sdmse.automation.core.specs.windowLauncherDefaultSettings
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
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

    private val mainPlan: suspend AutomationExplorer.Context.(Installed) -> Unit = plan@{ pkg ->
        log(TAG, INFO) { "Executing plan for ${pkg.installId} with context $this" }

        run {
            val storageEntryLabels =
                aospLabels.getStorageEntryDynamic(this) + aospLabels.getStorageEntryStatic(this)
            log(TAG) { "storageEntryLabels=${storageEntryLabels.toVisualStrings()}" }

            val storageFinder = storageEntryFinder.storageFinderAOSP(storageEntryLabels, pkg)

            val action: suspend StepContext.() -> Boolean = action@{
                val target = storageFinder(this) ?: return@action false
                log(TAG) { "Storage entry target: $target" }
                when {
                    hasApiLevel(35) -> {
                        val mapped = findClickableParent(maxNesting = 3, node = target)
                        if (mapped != null) {
                            clickNormal(node = mapped)
                        } else {
                            log(TAG, WARN) { "No clickable parent (API 35+), trying gesture..." }
                            clickGesture(node = target)
                        }
                    }

                    else -> {
                        val mapped = findClickableParent(maxNesting = 6, node = target) ?: return@action false
                        clickNormal(node = mapped)
                    }
                }
            }

            val step = AutomationStep(
                source = tag,
                descriptionInternal = "Storage entry",
                label = R.string.appcleaner_automation_progress_find_storage.toCaString(storageEntryLabels),
                windowLaunch = windowLauncherDefaultSettings(pkg),
                windowCheck = windowCheckDefaultSettings(SETTINGS_PKG, ipcFunnel, pkg),
                nodeRecovery = defaultNodeRecovery(pkg),
                nodeAction = action,
            )
            stepper.withProgress(this) { process(this@plan, step) }
        }

        run {
            val clearCacheButtonLabels =
                aospLabels.getClearCacheDynamic(this) + aospLabels.getClearCacheStatic(this)
            log(TAG) { "clearCacheButtonLabels=${clearCacheButtonLabels.toVisualStrings()}" }

            val nodeAction: suspend StepContext.() -> Boolean = action@{
                var candidate = findNodeByLabel(clearCacheButtonLabels)
                log(tag) { "Potential target is $candidate" }
                if (candidate == null) return@action false

                val clickableParent = findClickableParent(node = candidate)
                log(tag, VERBOSE) { "Clickable parent is $clickableParent" }
                val clickableSibling = findClickableSibling(node = candidate)
                log(tag, VERBOSE) { "Clickable sibling is $clickableSibling" }

                val target = when {
                    // ----------10: text='null', class=android.widget.LinearLayout, clickable=false, checkable=false enabled=true, id=null
                    // -----------11: text='Clear storage', class=android.widget.Button, clickable=true, checkable=false enabled=true, id=com.android.settings:id/button1
                    // -----------11: text='null', class=android.view.View, clickable=false, checkable=false enabled=true, id=com.android.settings:id/divider1
                    // -----------11: text='Clear cache', class=android.widget.Button, clickable=true, checkable=false enabled=true, id=com.android.settings:id/button2
                    candidate.isClickyButton() -> candidate.also { log(tag) { "Target is clicky button: $it" } }

                    // -----------11: text='null', class=android.widget.LinearLayout, clickable=true, checkable=false enabled=true, id=com.android.settings:id/action2
                    // ------------12: text='null', class=android.widget.Button, clickable=true, checkable=false enabled=true, id=com.android.settings:id/button2
                    // ------------12: text='Clear cache', class=android.widget.TextView, clickable=true, checkable=false enabled=true, id=com.android.settings:id/text2
                    clickableParent != null -> clickableParent.also { log(tag) { "Target is clickable parent: $it" } }

                    //-----------11: text='null', class=android.widget.LinearLayout, clickable=false, checkable=false enabled=true, id=com.android.settings:id/action2 pkg=com.android.settings, identity=bfaf7f6, bounds=Rect(540, 959 - 1020, 1239)
                    //------------12: text='null', class=android.widget.Button, clickable=true, checkable=false enabled=true, id=com.android.settings:id/button2 pkg=com.android.settings, identity=1eadc93, bounds=Rect(691, 959 - 869, 1098)
                    //------------12: text='Borrar caché', class=android.widget.TextView, clickable=false, checkable=false enabled=true, id=com.android.settings:id/text2 pkg=com.android.settings, identity=6d69d82, bounds=Rect(628, 1113 - 931, 1181)
                    clickableSibling != null -> clickableSibling.also { log(tag) { "Target is clickable sibling: $it" } }

                    else -> null
                }

                if (target == null) {
                    log(tag, WARN) { "Mapped target for 'Clear cache' is null?" }
                    return@action false
                }

                log(tag) { "Clicking 'Clear cache' target $target for $pkg:" }
                clickClearCache(isDryRun = Bugs.isDryRun, pkg = pkg, node = target)
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
