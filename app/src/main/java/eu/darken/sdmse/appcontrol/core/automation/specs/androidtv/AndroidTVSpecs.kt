package eu.darken.sdmse.appcontrol.core.automation.specs.androidtv

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.core.automation.specs.alcatel.AlcatelSpecs
import eu.darken.sdmse.appcontrol.core.automation.specs.AppControlSpecGenerator
import eu.darken.sdmse.automation.core.common.Stepper
import eu.darken.sdmse.automation.core.common.clickableParent
import eu.darken.sdmse.automation.core.common.crawl
import eu.darken.sdmse.automation.core.common.defaultClick
import eu.darken.sdmse.automation.core.common.getDefaultNodeRecovery
import eu.darken.sdmse.automation.core.common.getSysLocale
import eu.darken.sdmse.automation.core.common.pkgId
import eu.darken.sdmse.automation.core.common.textMatchesAny
import eu.darken.sdmse.automation.core.common.windowCheck
import eu.darken.sdmse.automation.core.common.windowCheckDefaultSettings
import eu.darken.sdmse.automation.core.common.windowLauncherDefaultSettings
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.automation.core.specs.AutomationSpec
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
open class AndroidTVSpecs @Inject constructor(
    private val ipcFunnel: IPCFunnel,
    @ApplicationContext private val context: Context,
    private val deviceDetective: DeviceDetective,
    private val tvLabels: AndroidTVLabels,
    private val generalSettings: GeneralSettings,
    private val stepper: Stepper,
) : AppControlSpecGenerator {

    override val tag: String = TAG

    override suspend fun isResponsible(pkg: Installed): Boolean {
        val romType = generalSettings.romTypeDetection.value()
        if (romType == RomType.ANDROID_TV) return true
        if (romType != RomType.AUTO) return false

        return deviceDetective.getROMType() == RomType.ANDROID_TV
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

        val forceStopLabels = tvLabels.getForceStopButtonDynamic()
        var wasDisabled = false

        run {
            val step = Stepper.Step(
                source = TAG,
                descriptionInternal = "Force stop button",
                label = R.string.appcontrol_automation_progress_find_force_stop.toCaString(forceStopLabels),
                windowLaunch = windowLauncherDefaultSettings(pkg),
                windowCheck = windowCheckDefaultSettings(AlcatelSpecs.SETTINGS_PKG, ipcFunnel, pkg),
                nodeTest = storageFilter@{ node ->
                    node.textMatchesAny(forceStopLabels)
                },
                nodeRecovery = getDefaultNodeRecovery(pkg),
                nodeMapping = clickableParent(),
                action = defaultClick(onDisabled = {
                    wasDisabled = true
                    true
                }),
            )
            stepper.withProgress(this) { process(this@plan, step) }
        }

        if (wasDisabled) {
            log(TAG) { "Force stop button was disabled, app is already stopped." }
            return@plan
        }

        run {
            val okLbl = tvLabels.getForceStopDialogOkDynamic()
            val cancelLbl = tvLabels.getForceStopDialogCancelDynamic()

            val windowCheck = windowCheck { _, root ->
                if (root.pkgId != SETTINGS_PKG) return@windowCheck false
                root.crawl().map { it.node }.any { subNode ->
                    subNode.viewIdResourceName?.contains("guidance_container") == true
                }
            }

            val buttonFilter = fun(node: AccessibilityNodeInfo): Boolean = when (Bugs.isDryRun) {
                true -> node.textMatchesAny(cancelLbl)
                false -> node.textMatchesAny(okLbl)
            }

            val step = Stepper.Step(
                source = TAG,
                descriptionInternal = "Confirm force stop",
                label = R.string.appcleaner_automation_progress_find_ok_confirmation.toCaString(okLbl),
                windowCheck = windowCheck,
                nodeTest = buttonFilter,
                nodeMapping = clickableParent(),
                action = defaultClick(),
            )
            stepper.withProgress(this) { process(this@plan, step) }
        }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: AndroidTVSpecs): AppControlSpecGenerator
    }

    companion object {
        val SETTINGS_PKG = "com.android.tv.settings".toPkgId()
        val TAG: String = logTag("AppControl", "Automation", "AndroidTV", "Specs")
    }
}
