package eu.darken.sdmse.appcleaner.core.automation.specs.coloros

import android.view.accessibility.AccessibilityNodeInfo
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.appcleaner.core.automation.specs.AppCleanerSpecGenerator
import eu.darken.sdmse.appcleaner.core.automation.specs.OnTheFlyLabler
import eu.darken.sdmse.automation.core.common.StepProcessor
import eu.darken.sdmse.automation.core.common.clickableParent
import eu.darken.sdmse.automation.core.common.crawl
import eu.darken.sdmse.automation.core.common.defaultClick
import eu.darken.sdmse.automation.core.common.defaultWindowFilter
import eu.darken.sdmse.automation.core.common.defaultWindowIntent
import eu.darken.sdmse.automation.core.common.getAospClearCacheClick
import eu.darken.sdmse.automation.core.common.getDefaultNodeRecovery
import eu.darken.sdmse.automation.core.common.getSysLocale
import eu.darken.sdmse.automation.core.common.idMatches
import eu.darken.sdmse.automation.core.common.textMatchesAny
import eu.darken.sdmse.automation.core.common.windowCriteria
import eu.darken.sdmse.automation.core.common.windowCriteriaAppIdentifier
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.automation.core.specs.AutomationSpec
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
import java.util.*
import javax.inject.Inject

@Reusable
class ColorOSSpecs @Inject constructor(
    private val ipcFunnel: IPCFunnel,
    private val deviceDetective: DeviceDetective,
    private val colorOSLabels: ColorOSLabels,
    private val onTheFlyLabler: OnTheFlyLabler,
    private val generalSettings: GeneralSettings,
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
        override suspend fun createPlan(): suspend AutomationExplorer.Context.() -> Unit = {
            mainPlan(pkg)
        }
    }

    private val mainPlan: suspend AutomationExplorer.Context.(Installed) -> Unit = { pkg ->
        log(TAG, INFO) { "Executing plan for ${pkg.installId} with context $this" }

        val locale = getSysLocale()
        val lang = locale.language
        val script = locale.script

        log(VERBOSE) { "Getting specs for ${pkg.packageName} (lang=$lang, script=$script)" }

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
                colorOSLabels.getStorageEntryDynamic() + colorOSLabels.getStorageEntryLabels(lang, script)

            val storageFilter = onTheFlyLabler.getAOSPStorageFilter(storageEntryLabels, pkg)

            val step = StepProcessor.Step(
                parentTag = TAG,
                label = "Find & click 'Storage Usage' (targets=$storageEntryLabels)",
                windowIntent = defaultWindowIntent(pkg),
                windowEventFilter = defaultWindowFilter(SETTINGS_PKG),
                windowNodeTest = windowCriteriaAppIdentifier(SETTINGS_PKG, ipcFunnel, pkg),
                nodeTest = storageFilter,
                nodeRecovery = getDefaultNodeRecovery(pkg),
                nodeMapping = clickableParent(),
                action = defaultClick()
            )

            stepper.withProgress(this) { process(step) }
        }

        run {
            // 16: className=android.widget.Button, text=Clear Cache, isClickable=true, isEnabled=true, viewIdResourceName=com.android.settings:id/button, pkgName=com.android.settings

            val clearCacheButtonLabels =
                colorOSLabels.getClearCacheDynamic() + colorOSLabels.getClearCacheLabels(lang, script)

            val buttonFilter = fun(node: AccessibilityNodeInfo): Boolean {
                return node.textMatchesAny(clearCacheButtonLabels)
            }

            val recognizesName = windowCriteriaAppIdentifier(SETTINGS_PKG, ipcFunnel, pkg)
            // https://github.com/d4rken/sdmaid-public/issues/4939
            val hasClearCacheButton = windowCriteria(SETTINGS_PKG) { node ->
                node.crawl().map { it.node }
                    .any { toTest -> toTest.idMatches("com.android.settings:id/clear_cache_button") }
            }
            val combined: suspend (AccessibilityNodeInfo) -> Boolean = { node: AccessibilityNodeInfo ->
                recognizesName.invoke(node) || hasClearCacheButton.invoke(node)
            }

            val step = StepProcessor.Step(
                parentTag = TAG,
                label = "Find & click 'Clear Cache' (targets=$clearCacheButtonLabels)",
                windowNodeTest = combined,
                nodeTest = buttonFilter,
                nodeRecovery = getDefaultNodeRecovery(pkg),
                action = getAospClearCacheClick(pkg, TAG)
            )
            stepper.withProgress(this) { process(step) }
        }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: ColorOSSpecs): AppCleanerSpecGenerator
    }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "ColorOS", "Specs")
        val SETTINGS_PKG = "com.android.settings".toPkgId()
    }
}