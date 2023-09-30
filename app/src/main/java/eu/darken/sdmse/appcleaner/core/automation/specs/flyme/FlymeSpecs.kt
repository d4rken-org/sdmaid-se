package eu.darken.sdmse.appcleaner.core.automation.specs.flyme

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
import eu.darken.sdmse.automation.core.common.idContains
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
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.isInstalled
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.progress.withProgress
import java.util.*
import javax.inject.Inject

@Reusable
class FlymeSpecs @Inject constructor(
    private val ipcFunnel: IPCFunnel,
    @ApplicationContext private val context: Context,
    private val deviceDetective: DeviceDetective,
    private val pkgRepo: PkgRepo,
    private val flymeLabels: FlymeLabels,
    private val settings: AppCleanerSettings,
) : ExplorerSpecGenerator() {

    override val label = TAG.toCaString()

    override val tag: String = TAG

    // https://github.com/d4rken/sdmaid-public/issues/2910
    override suspend fun isResponsible(pkg: Installed): Boolean {
        if (settings.romTypeDetection.value() == SpecRomType.FLYME) return true
        if (deviceDetective.isCustomROM()) return false
        if (!deviceDetective.isMeizu()) return false

        // Meizu/meizu_M8/meizuM8:8.1.0/O11019/1540458380:user/release-keys
        return pkgRepo.isInstalled("com.meizu.flyme.update".toPkgId())
    }

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
            // Do this beforehand so we crash early if unsupported
            val clearCacheButtonLabels = flymeLabels.getClearCacheLabel()?.let { setOf(it) }
                ?: flymeLabels.getClearCacheLabels(lang, script)

            val buttonFilter = fun(node: AccessibilityNodeInfo): Boolean {
                if (!node.isClickable) return false
                // viewIdResName: com.android.settings:id/right_text
                if (!node.idContains("right_text")) return false
                return node.textMatchesAny(clearCacheButtonLabels)
            }

            val step = StepProcessor.Step(
                parentTag = TAG,
                label = "Find & click 'Clear Cache' (targets=$clearCacheButtonLabels)",
                windowIntent = CrawlerCommon.defaultWindowIntent(context, pkg),
                windowEventFilter = CrawlerCommon.defaultWindowFilter(SETTINGS_PKG),
                windowNodeTest = CrawlerCommon.windowCriteriaAppIdentifier(SETTINGS_PKG, ipcFunnel, pkg),
                nodeTest = buttonFilter,
                nodeRecovery = CrawlerCommon.getDefaultNodeRecovery(pkg),
                action = getDefaultClearCacheClick(pkg, TAG)
            )
            stepper.withProgress(this) { process(step) }
        }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: FlymeSpecs): SpecGenerator
    }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "Flyme", "Specs")
        val SETTINGS_PKG = "com.android.settings".toPkgId()
    }
}