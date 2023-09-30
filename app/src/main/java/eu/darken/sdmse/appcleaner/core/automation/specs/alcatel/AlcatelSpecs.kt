package eu.darken.sdmse.appcleaner.core.automation.specs.alcatel

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
import eu.darken.sdmse.automation.core.common.StepProcessor
import eu.darken.sdmse.automation.core.common.idContains
import eu.darken.sdmse.automation.core.common.isClickyButton
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
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.progress.withProgress
import javax.inject.Inject

@Reusable
class AlcatelSpecs @Inject constructor(
    ipcFunnel: IPCFunnel,
    @ApplicationContext private val context: Context,
    private val deviceDetective: DeviceDetective,
    private val alcatelLabels: AlcatelLabels,
    private val settings: AppCleanerSettings,
) : ExplorerSpecGenerator() {

    override val label = TAG.toCaString()

    override val tag: String = TAG

    override suspend fun isResponsible(pkg: Installed): Boolean {
        if (settings.romTypeDetection.value() == SpecRomType.ALCATEL) return true
        if (deviceDetective.isCustomROM()) return false

        // Android 10 Alcatel ROMs still use older labels
        return hasApiLevel(29) && deviceDetective.isAlcatel()
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
            val storageEntryLabels = alcatelLabels.getStorageEntryDynamic()
                ?: alcatelLabels.getStorageEntryStatic(lang, script)

            val storageFilter = fun(node: AccessibilityNodeInfo): Boolean {
                if (!node.isTextView()) return false
                if (!hasApiLevel(33) && !node.idContains("android:id/title")) return false
                return node.textMatchesAny(storageEntryLabels)
            }

            val step = StepProcessor.Step(
                parentTag = tag,
                label = "Find & click 'Storage' (targets=$storageEntryLabels)",
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
            val clearCacheButtonLabels = alcatelLabels.getClearCacheDynamic()
                ?: alcatelLabels.getClearCacheStatic(lang, script)

            val buttonFilter = fun(node: AccessibilityNodeInfo): Boolean {
                if (!node.isClickyButton()) return false
                return node.textMatchesAny(clearCacheButtonLabels)
            }

            val step = StepProcessor.Step(
                parentTag = tag,
                label = "Find & click 'Clear Cache' (targets=$clearCacheButtonLabels)",
                windowNodeTest = CrawlerCommon.windowCriteriaAppIdentifier(SETTINGS_PKG, ipcFunnel, pkg),
                nodeTest = buttonFilter,
                action = CrawlerCommon.getDefaultClearCacheClick(pkg, tag)
            )
            stepper.withProgress(this) { process(step) }
        }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: AlcatelSpecs): SpecGenerator
    }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "Alcatel", "Specs")
        val SETTINGS_PKG = "com.android.settings".toPkgId()
    }
}