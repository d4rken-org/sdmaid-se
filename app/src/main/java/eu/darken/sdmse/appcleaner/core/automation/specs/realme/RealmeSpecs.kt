package eu.darken.sdmse.appcleaner.core.automation.specs.realme

import android.view.accessibility.AccessibilityNodeInfo
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.core.automation.specs.AppCleanerSpecGenerator
import eu.darken.sdmse.appcleaner.core.automation.specs.OnTheFlyLabler
import eu.darken.sdmse.automation.core.common.Stepper
import eu.darken.sdmse.automation.core.common.clickableParent
import eu.darken.sdmse.automation.core.common.defaultClick
import eu.darken.sdmse.automation.core.common.getAospClearCacheClick
import eu.darken.sdmse.automation.core.common.getDefaultNodeRecovery
import eu.darken.sdmse.automation.core.common.getSysLocale
import eu.darken.sdmse.automation.core.common.isClickyButton
import eu.darken.sdmse.automation.core.common.pkgId
import eu.darken.sdmse.automation.core.common.textMatchesAny
import eu.darken.sdmse.automation.core.common.windowCheck
import eu.darken.sdmse.automation.core.common.windowCheckDefaultSettings
import eu.darken.sdmse.automation.core.common.windowLauncherDefaultSettings
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.automation.core.specs.AutomationSpec
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
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
class RealmeSpecs @Inject constructor(
    ipcFunnel: IPCFunnel,
    private val deviceDetective: DeviceDetective,
    private val realmeLabels: RealmeLabels,
    private val onTheFlyLabler: OnTheFlyLabler,
    private val generalSettings: GeneralSettings,
    private val stepper: Stepper,
) : AppCleanerSpecGenerator {

    override val tag: String = TAG

    // https://github.com/d4rken/sdmaid-public/issues/3040
    override suspend fun isResponsible(pkg: Installed): Boolean {
        val romType = generalSettings.romTypeDetection.value()
        if (romType == RomType.REALME) return true
        if (romType != RomType.AUTO) return false

        return deviceDetective.getROMType() == RomType.REALME
    }

    override suspend fun getClearCache(pkg: Installed): AutomationSpec = object : AutomationSpec.Explorer {
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

        run {
            val storageEntryLabels =
                realmeLabels.getStorageEntryDynamic() + realmeLabels.getStorageEntryLabels(lang, script)
            log(TAG) { "storageEntryLabels=$storageEntryLabels" }

            val storageFilter = onTheFlyLabler.getAOSPStorageFilter(storageEntryLabels, pkg)

            val step = Stepper.Step(
                source = TAG,
                descriptionInternal = "Storage entry",
                label = R.string.appcleaner_automation_progress_find_storage.toCaString(storageEntryLabels),
                windowLaunch = windowLauncherDefaultSettings(pkg),
                windowCheck = windowCheckDefaultSettings(SETTINGS_PKG, ipcFunnel, pkg),
                nodeTest = storageFilter,
                nodeRecovery = getDefaultNodeRecovery(pkg),
                nodeMapping = clickableParent(),
                action = defaultClick()
            )
            stepper.withProgress(this) { process(this@plan, step) }
        }

        run {
            val clearCacheButtonLabels =
                realmeLabels.getClearCacheDynamic() + realmeLabels.getClearCacheLabels(lang, script)
            log(TAG) { "clearCacheButtonLabels=$clearCacheButtonLabels" }

            var isUnclickableButton = false
            val buttonFilter = when {
                hasApiLevel(35) -> fun(node: AccessibilityNodeInfo): Boolean {
                    if (!node.textMatchesAny(clearCacheButtonLabels)) return false
                    isUnclickableButton = !node.isClickyButton()
                    return true
                }

                else -> fun(node: AccessibilityNodeInfo): Boolean {
                    return node.isClickyButton() && node.textMatchesAny(clearCacheButtonLabels)
                }
            }


            val step = Stepper.Step(
                source = TAG,
                descriptionInternal = "Clear cache",
                label = R.string.appcleaner_automation_progress_find_clear_cache.toCaString(clearCacheButtonLabels),
                windowCheck = windowCheck { _, root -> root.pkgId == SETTINGS_PKG },
                nodeTest = buttonFilter,
                nodeMapping = when {
                    hasApiLevel(35) -> {
                        // Function that is evaluated later, has access to vars in this scope
                        { node ->
                            when {
                                isUnclickableButton -> clickableParent().invoke(node)
                                else -> node
                            }
                        }
                    }

                    else -> null
                },
                action = getAospClearCacheClick(pkg, tag)
            )
            stepper.withProgress(this) { process(this@plan, step) }
        }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: RealmeSpecs): AppCleanerSpecGenerator
    }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "Realme", "Specs")
        val SETTINGS_PKG = "com.android.settings".toPkgId()
    }
}