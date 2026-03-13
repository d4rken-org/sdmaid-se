package eu.darken.sdmse.appcleaner.core.automation.specs.oxygenos

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
import eu.darken.sdmse.automation.core.common.stepper.findNodeByLabel
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
class OxygenOSSpecs @Inject constructor(
    private val ipcFunnel: IPCFunnel,
    private val deviceDetective: DeviceDetective,
    private val oxygenOSLabels: OxygenOSLabels,
    private val storageEntryFinder: StorageEntryFinder,
    private val generalSettings: GeneralSettings,
    private val stepper: Stepper,
) : AppCleanerSpecGenerator {

    override val tag: String = TAG

    override suspend fun isResponsible(pkg: Installed): Boolean {
        val romType = generalSettings.romTypeDetection.value()
        if (romType == RomType.OXYGENOS) return true
        if (romType != RomType.AUTO) return false

        return deviceDetective.getROMType() == RomType.OXYGENOS
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
                oxygenOSLabels.getStorageEntryDynamic(this) + oxygenOSLabels.getStorageEntryLabels(this)
            log(TAG) { "storageEntryLabels=${storageEntryLabels.toVisualStrings()}" }

            val storageFinder = storageEntryFinder.storageFinderAOSP(storageEntryLabels, pkg)

            val step = AutomationStep(
                source = TAG,
                descriptionInternal = "Storage entry for $pkg",
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
                oxygenOSLabels.getClearCacheDynamic(this) + oxygenOSLabels.getClearCacheStatic(this)

            log(TAG) { "clearCacheButtonLabels=${clearCacheButtonLabels.toVisualStrings()}" }

            val action: suspend StepContext.() -> Boolean = action@{
                val target = when {
                    hasApiLevel(34) -> findNodeByLabel(clearCacheButtonLabels)
                    else -> findNodeByLabel(clearCacheButtonLabels) { it.isClickyButton() }
                } ?: return@action false

                // On API 34+, button text may be nested in non-clickable wrapper, find clickable parent.
                // If no clickable parent exists (e.g., disabled button when cache=0), fall back to target
                // and let clickClearCache() handle the disabled state.
                val mapped = when {
                    hasApiLevel(34) && !target.isClickyButton() -> findClickableParent(node = target) ?: target
                    else -> target
                }

                clickClearCache(isDryRun = Bugs.isDryRun, pkg, node = mapped)
            }

            val step = AutomationStep(
                source = TAG,
                descriptionInternal = "Clear cache for $pkg",
                label = R.string.appcleaner_automation_progress_find_clear_cache.toCaString(clearCacheButtonLabels),
                windowCheck = windowCheckDefaultSettings(SETTINGS_PKG, ipcFunnel, pkg),
                nodeAction = action,
            )
            stepper.withProgress(this) { process(this@plan, step) }
        }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: OxygenOSSpecs): AppCleanerSpecGenerator
    }

    companion object {
        val SETTINGS_PKG = "com.android.settings".toPkgId()

        private val TAG: String = logTag("AppCleaner", "Automation", "OnePlus", "Specs")
    }

}
