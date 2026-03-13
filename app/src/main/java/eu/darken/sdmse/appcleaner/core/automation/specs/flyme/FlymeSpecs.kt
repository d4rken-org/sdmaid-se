package eu.darken.sdmse.appcleaner.core.automation.specs.flyme

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.core.automation.specs.AppCleanerSpecGenerator
import eu.darken.sdmse.appcleaner.core.automation.specs.defaultFindAndClickClearCache
import eu.darken.sdmse.automation.core.common.idContains
import eu.darken.sdmse.automation.core.common.stepper.AutomationStep
import eu.darken.sdmse.automation.core.common.stepper.Stepper
import eu.darken.sdmse.automation.core.common.textMatchesAny
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.automation.core.specs.AutomationSpec
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
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.progress.withProgress
import eu.darken.sdmse.main.core.GeneralSettings
import javax.inject.Inject

@Reusable
class FlymeSpecs @Inject constructor(
    private val ipcFunnel: IPCFunnel,
    private val deviceDetective: DeviceDetective,
    private val flymeLabels: FlymeLabels,
    private val generalSettings: GeneralSettings,
    private val stepper: Stepper,
) : AppCleanerSpecGenerator {

    override val tag: String = TAG

    // https://github.com/d4rken/sdmaid-public/issues/2910
    override suspend fun isResponsible(pkg: Installed): Boolean {
        val romType = generalSettings.romTypeDetection.value()
        if (romType == RomType.FLYMEOS) return true
        if (romType != RomType.AUTO) return false

        // Meizu/meizu_M8/meizuM8:8.1.0/O11019/1540458380:user/release-keys
        return deviceDetective.getROMType() == RomType.FLYMEOS
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
            // Do this beforehand so we crash early if unsupported
            val clearCacheButtonLabels =
                flymeLabels.getClearCacheDynamic(this) + flymeLabels.getClearCacheLabels(this)
            log(TAG) { "clearCacheButtonLabels=${clearCacheButtonLabels.toVisualStrings()}" }

            if (clearCacheButtonLabels.isNotEmpty()) {
                log(TAG, WARN) { "clearCacheButtonLabels was empty" }
                throw UnsupportedOperationException("This system language is not supported")
            }

            val action = defaultFindAndClickClearCache(isDryRun = Bugs.isDryRun, pkg) { node ->
                if (!node.isClickable) return@defaultFindAndClickClearCache false
                // viewIdResName: com.android.settings:id/right_text
                if (!node.idContains("right_text")) return@defaultFindAndClickClearCache false
                node.textMatchesAny(clearCacheButtonLabels)
            }

            val step = AutomationStep(
                source = TAG,
                descriptionInternal = "Clear cache button for $pkg",
                label = R.string.appcleaner_automation_progress_find_clear_cache.toCaString(clearCacheButtonLabels),
                windowLaunch = windowLauncherDefaultSettings(pkg),
                windowCheck = windowCheckDefaultSettings(SETTINGS_PKG, ipcFunnel, pkg),
                nodeRecovery = defaultNodeRecovery(pkg),
                nodeAction = action,
            )
            stepper.withProgress(this) { process(this@plan, step) }
        }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: FlymeSpecs): AppCleanerSpecGenerator
    }

    companion object {
        private val TAG: String = logTag("AppCleaner", "Automation", "Flyme", "Specs")
        val SETTINGS_PKG = "com.android.settings".toPkgId()
    }
}