package eu.darken.sdmse.appcontrol.core.automation.specs.samsung

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.R
import eu.darken.sdmse.appcontrol.core.automation.specs.AppControlSpecGenerator
import eu.darken.sdmse.appcontrol.core.automation.specs.aosp.AOSPSpecs
import eu.darken.sdmse.automation.core.common.crawl
import eu.darken.sdmse.automation.core.common.getSysLocale
import eu.darken.sdmse.automation.core.common.pkgId
import eu.darken.sdmse.automation.core.common.stepper.AutomationStep
import eu.darken.sdmse.automation.core.common.stepper.StepContext
import eu.darken.sdmse.automation.core.common.stepper.Stepper
import eu.darken.sdmse.automation.core.common.stepper.clickNormal
import eu.darken.sdmse.automation.core.common.stepper.findClickableParent
import eu.darken.sdmse.automation.core.common.stepper.findNode
import eu.darken.sdmse.automation.core.common.textMatchesAny
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.automation.core.specs.AutomationSpec
import eu.darken.sdmse.automation.core.specs.defaultNodeRecovery
import eu.darken.sdmse.automation.core.specs.windowCheck
import eu.darken.sdmse.automation.core.specs.windowCheckDefaultSettings
import eu.darken.sdmse.automation.core.specs.windowLauncherDefaultSettings
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.device.DeviceDetective
import eu.darken.sdmse.common.device.RomType
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.progress.withProgress
import eu.darken.sdmse.main.core.GeneralSettings
import javax.inject.Inject

@Reusable
class SamsungSpecs @Inject constructor(
    private val ipcFunnel: IPCFunnel,
    private val deviceDetective: DeviceDetective,
    private val samsungLabels: SamsungLabels,
    private val generalSettings: GeneralSettings,
    private val stepper: Stepper,
) : AppControlSpecGenerator {

    override val tag: String = TAG

    override suspend fun isResponsible(pkg: Installed): Boolean {
        val romType = generalSettings.romTypeDetection.value()
        if (romType == RomType.SAMSUNG) return true
        if (romType != RomType.AUTO) return false

        return deviceDetective.getROMType() == RomType.SAMSUNG
    }

    override suspend fun getForceStop(pkg: Installed): AutomationSpec = object : AutomationSpec.Explorer {
        override val tag: String = TAG
        override suspend fun createPlan(): suspend AutomationExplorer.Context.() -> Unit = {
            mainPlan(pkg)
        }
    }

    private val mainPlan: suspend AutomationExplorer.Context.(Installed) -> Unit = plan@{ pkg ->
        log(TAG, INFO) { "Executing plan for ${pkg.installId} with context $this" }

        val locale = getSysLocale()
        val lang = locale.language
        val script = locale.script

        log(VERBOSE) { "Getting specs for ${pkg.packageName} (lang=$lang, script=$script)" }

        val forceStopLabels = samsungLabels.getForceStopButtonDynamic()
        var wasDisabled = false

        run {
            val action: suspend StepContext.() -> Boolean = action@{
                val target = findNode { it.textMatchesAny(forceStopLabels) } ?: return@action false
                val mapped = findClickableParent(includeSelf = true, node = target) ?: return@action false
                if (mapped.isEnabled) {
                    clickNormal(node = mapped)
                } else {
                    wasDisabled = true
                    true
                }
            }

            val step = AutomationStep(
                source = TAG,
                descriptionInternal = "Force stop button",
                label = R.string.appcontrol_automation_progress_find_force_stop.toCaString(forceStopLabels),
                windowLaunch = windowLauncherDefaultSettings(pkg),
                windowCheck = windowCheckDefaultSettings(SETTINGS_PKG, ipcFunnel, pkg),
                nodeRecovery = defaultNodeRecovery(pkg),
                nodeAction = action,
            )
            stepper.withProgress(this) { process(this@plan, step) }
        }

        if (wasDisabled) {
            log(AOSPSpecs.TAG) { "Force stop button was disabled, app is already stopped." }
            return@plan
        }

        run {
            val titleLbl = samsungLabels.getForceStopDialogTitleDynamic() + forceStopLabels.map { "$it?" }
            val okLbl = samsungLabels.getForceStopDialogOkDynamic()
            val cancelLbl = samsungLabels.getForceStopDialogCancelDynamic()

            val windowCheck = windowCheck { _, root ->
                if (root.pkgId != SETTINGS_PKG) return@windowCheck false
                root.crawl().map { it.node }.any { subNode -> subNode.textMatchesAny(titleLbl) }
            }

            val action: suspend StepContext.() -> Boolean = action@{
                val target = findNode {
                    when (Bugs.isDryRun) {
                        true -> it.textMatchesAny(cancelLbl)
                        false -> it.textMatchesAny(okLbl)
                    }
                } ?: return@action false
                val mapped = findClickableParent(includeSelf = true, node = target) ?: return@action false
                clickNormal(node = mapped)
            }

            val step = AutomationStep(
                source = TAG,
                descriptionInternal = "Confirm force stop",
                label = R.string.appcleaner_automation_progress_find_ok_confirmation.toCaString(titleLbl + okLbl),
                windowCheck = windowCheck,
                nodeAction = action,
            )
            stepper.withProgress(this) { process(this@plan, step) }
        }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: SamsungSpecs): AppControlSpecGenerator
    }

    companion object {
        val SETTINGS_PKG = "com.android.settings".toPkgId()

        val TAG: String = logTag("AppControl", "Automation", "Samsung", "Specs")
    }

}
