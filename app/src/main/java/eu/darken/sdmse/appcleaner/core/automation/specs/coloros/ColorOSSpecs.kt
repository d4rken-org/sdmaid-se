package eu.darken.sdmse.appcleaner.core.automation.specs.coloros

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.appcleaner.core.AppCleanerSettings
import eu.darken.sdmse.appcleaner.core.automation.specs.SpecRomType
import eu.darken.sdmse.automation.core.common.CrawlerCommon
import eu.darken.sdmse.automation.core.common.CrawlerCommon.getDefaultClearCacheClick
import eu.darken.sdmse.automation.core.common.StepProcessor
import eu.darken.sdmse.automation.core.common.crawl
import eu.darken.sdmse.automation.core.common.idContains
import eu.darken.sdmse.automation.core.common.idMatches
import eu.darken.sdmse.automation.core.common.isTextView
import eu.darken.sdmse.automation.core.common.textMatchesAny
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.automation.core.specs.AutomationSpec
import eu.darken.sdmse.automation.core.specs.ExplorerSpecGenerator
import eu.darken.sdmse.automation.core.specs.SpecGenerator
import eu.darken.sdmse.common.DeviceDetective
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.isInstalled
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.progress.withProgress
import java.util.*
import javax.inject.Inject

@Reusable
class ColorOSSpecs @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ipcFunnel: IPCFunnel,
    private val deviceDetective: DeviceDetective,
    private val pkgRepo: PkgRepo,
    private val colorOSLabels: ColorOSLabels,
    private val settings: AppCleanerSettings,
) : ExplorerSpecGenerator() {

    override val label = TAG.toCaString()

    override val tag: String = TAG

    // https://github.com/d4rken/sdmaid-public/issues/2910
    override suspend fun isResponsible(pkg: Installed): Boolean {
        if (settings.romTypeDetection.value() == SpecRomType.COLOROS) return true
        if (deviceDetective.isCustomROM()) return false
        if (!deviceDetective.isOppo()) return false
        return hasApiLevel(26) && hasColorApps()
    }

    private suspend fun hasColorApps(): Boolean = listOf(
        // Not available on OPPO/CPH2247EEA/OP4F7FL1:11/RKQ1.201105.002/1632415665086:user/release-keys
        "com.coloros.simsettings",
        "com.coloros.filemanager"
    ).any { pkgRepo.isInstalled(it.toPkgId()) }

    override suspend fun getSpec(pkg: Installed): AutomationSpec = object : AutomationSpec.Explorer {
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

            val storageEntryLabels = colorOSLabels.getStorageEntryLabel()?.let { setOf(it) }
                ?: colorOSLabels.getStorageEntryLabels(lang, script)

            val storageFilter = fun(node: AccessibilityNodeInfo): Boolean {
                if (!node.isTextView()) return false
                if (!hasApiLevel(33) && !node.idContains("android:id/title")) return false
                return node.textMatchesAny(storageEntryLabels)
            }


            val step = StepProcessor.Step(
                parentTag = TAG,
                label = "Find & click 'Storage Usage' (targets=$storageEntryLabels)",
                windowIntent = CrawlerCommon.defaultWindowIntent(context, pkg),
                windowEventFilter = CrawlerCommon.defaultWindowFilter(SETTINGS_PKG),
                windowNodeTest = CrawlerCommon.windowCriteriaAppIdentifier(SETTINGS_PKG, ipcFunnel, pkg),
                nodeTest = storageFilter,
                nodeRecovery = CrawlerCommon.getDefaultNodeRecovery(pkg),
                nodeMapping = CrawlerCommon.clickableParent(),
                action = CrawlerCommon.defaultClick()
            )

            stepper.withProgress(this) { process(step) }
        }

        run {
            // 16: className=android.widget.Button, text=Clear Cache, isClickable=true, isEnabled=true, viewIdResourceName=com.android.settings:id/button, pkgName=com.android.settings

            val clearCacheButtonLabels = colorOSLabels.getClearCacheLabel()?.let { setOf(it) }
                ?: colorOSLabels.getClearCacheLabels(lang, script)

            val buttonFilter = fun(node: AccessibilityNodeInfo): Boolean {
                return node.textMatchesAny(clearCacheButtonLabels)
            }

            val recognizesName = CrawlerCommon.windowCriteriaAppIdentifier(SETTINGS_PKG, ipcFunnel, pkg)
            // https://github.com/d4rken/sdmaid-public/issues/4939
            val hasClearCacheButton = CrawlerCommon.windowCriteria(SETTINGS_PKG) { node ->
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
                nodeRecovery = CrawlerCommon.getDefaultNodeRecovery(pkg),
                action = getDefaultClearCacheClick(pkg, TAG)
            )
            stepper.withProgress(this) { process(step) }
        }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: ColorOSSpecs): SpecGenerator
    }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "ColorOS", "Specs")
        val SETTINGS_PKG = "com.android.settings".toPkgId()
    }
}