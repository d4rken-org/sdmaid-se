package eu.darken.sdmse.appcleaner.core.automation.specs.coloros

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
import eu.darken.sdmse.automation.core.common.crawl
import eu.darken.sdmse.automation.core.common.idMatches
import eu.darken.sdmse.automation.core.common.isClickyButton
import eu.darken.sdmse.automation.core.common.pkgId
import eu.darken.sdmse.automation.core.common.stepper.AutomationStep
import eu.darken.sdmse.automation.core.common.stepper.StepContext
import eu.darken.sdmse.automation.core.common.stepper.Stepper
import eu.darken.sdmse.automation.core.common.stepper.findClickableParent
import eu.darken.sdmse.automation.core.common.stepper.findNode
import eu.darken.sdmse.automation.core.common.textMatchesAny
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.automation.core.specs.AutomationSpec
import eu.darken.sdmse.automation.core.specs.checkIdentifiers
import eu.darken.sdmse.automation.core.specs.defaultFindAndClick
import eu.darken.sdmse.automation.core.specs.defaultNodeRecovery
import eu.darken.sdmse.automation.core.specs.windowCheck
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
class ColorOSSpecs @Inject constructor(
    private val ipcFunnel: IPCFunnel,
    private val deviceDetective: DeviceDetective,
    private val colorOSLabels: ColorOSLabels,
    private val storageEntryFinder: StorageEntryFinder,
    private val generalSettings: GeneralSettings,
    private val stepper: Stepper,
) : AppCleanerSpecGenerator {

    override val tag: String = TAG

    // https://github.com/d4rken/sdmaid-public/issues/2910
    override suspend fun isResponsible(pkg: Installed): Boolean {
        val romType = generalSettings.romTypeDetection.value()
        if (romType == RomType.COLOROS) return true
        if (romType != RomType.AUTO) return false

        return hasApiLevel(26) && deviceDetective.getROMType() == RomType.COLOROS
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
            /**
             * 13: className=android.widget.LinearLayout, text=null, isClickable=true, isEnabled=true, viewIdResourceName=null, pkgName=com.android.settings
             *  14: className=android.widget.RelativeLayout, text=null, isClickable=false, isEnabled=true, viewIdResourceName=null, pkgName=com.android.settings
             *   15: className=android.widget.TextView, text=Storage Usage, isClickable=false, isEnabled=true, viewIdResourceName=android:id/title, pkgName=com.android.settings
             *   15: className=android.widget.TextView, text=715 MB, isClickable=false, isEnabled=true, viewIdResourceName=android:id/summary, pkgName=com.android.settings
             *  14: className=android.widget.LinearLayout, text=null, isClickable=false, isEnabled=true, viewIdResourceName=android:id/widget_frame, pkgName=com.android.settings
             *   15: className=android.widget.LinearLayout, text=null, isClickable=false, isEnabled=true, viewIdResourceName=null, pkgName=com.android.settings
             *    16: className=android.widget.LinearLayout, text=null, isClickable=false, isEnabled=true, viewIdResourceName=null, pkgName=com.android.settings
             *    16: className=android.widget.ImageView, text=null, isClickable=false, isEnabled=true, viewIdResourceName=com.android.settings:id/color_preference_widget_jump, pkgName=com.android.settings
             */

            val storageEntryLabels =
                colorOSLabels.getStorageEntryDynamic(this) + colorOSLabels.getStorageEntryLabels(this)
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
                colorOSLabels.getClearCacheDynamic(this) + colorOSLabels.getClearCacheLabels(this)
            log(TAG) { "clearCacheButtonLabels=${clearCacheButtonLabels.toVisualStrings()}" }

            val windowCheck = windowCheck { _, root ->
                if (root.pkgId != SETTINGS_PKG) return@windowCheck false
                if (checkIdentifiers(ipcFunnel, pkg)(root)) return@windowCheck true

                // https://github.com/d4rken/sdmaid-public/issues/4939
                val hasClearCacheButton = root.crawl().map { it.node }.any { toTest ->
                    toTest.idMatches("com.android.settings:id/clear_cache_button")
                }
                if (hasClearCacheButton) return@windowCheck true

                false
            }


            val action: suspend StepContext.() -> Boolean = action@{
                var isUnclickableButton = false
                val target = findNode { node ->
                    when {
                        //------------12: text='null', className=android.widget.FrameLayout, isClickable=false, isEnabled=true, viewIdResourceName=null, pkgName=com.android.settings, identity=ebb882b
                        //-------------13: text='null', className=android.widget.LinearLayout, isClickable=false, isEnabled=true, viewIdResourceName=null, pkgName=com.android.settings, identity=7f41bda
                        //--------------14: text='null', className=android.widget.RelativeLayout, isClickable=true, isEnabled=true, viewIdResourceName=com.android.settings:id/content_rl, pkgName=com.android.settings, identity=808780b
                        //---------------15: text='Clear cache', className=android.widget.Button, isClickable=false, isEnabled=true, viewIdResourceName=com.android.settings:id/button, pkgName=com.android.settings, identity=3b0a6e8
                        hasApiLevel(35) -> {
                            if (!node.textMatchesAny(clearCacheButtonLabels)) return@findNode false
                            isUnclickableButton = !node.isClickyButton()
                            true
                        }

                        // 16: className=android.widget.Button, text=Clear Cache, isClickable=true, isEnabled=true, viewIdResourceName=com.android.settings:id/button, pkgName=com.android.settings
                        else -> {
                            node.isClickyButton() && node.textMatchesAny(clearCacheButtonLabels)
                        }
                    }
                } ?: return@action false

                val mapped = when {
                    hasApiLevel(35) && isUnclickableButton -> findClickableParent(node = target)
                    else -> target
                } ?: return@action false

                clickClearCache(isDryRun = Bugs.isDryRun, pkg, node = mapped)
            }

            val step = AutomationStep(
                source = TAG,
                descriptionInternal = "Clear cache button for $pkg",
                label = R.string.appcleaner_automation_progress_find_clear_cache.toCaString(clearCacheButtonLabels),
                windowCheck = windowCheck,
                nodeRecovery = defaultNodeRecovery(pkg),
                nodeAction = action,
            )
            stepper.withProgress(this) { process(this@plan, step) }
        }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: ColorOSSpecs): AppCleanerSpecGenerator
    }

    companion object {
        private val TAG: String = logTag("AppCleaner", "Automation", "ColorOS", "Specs")
        val SETTINGS_PKG = "com.android.settings".toPkgId()
    }
}