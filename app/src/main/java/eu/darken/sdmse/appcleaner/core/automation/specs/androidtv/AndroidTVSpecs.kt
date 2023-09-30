package eu.darken.sdmse.appcleaner.core.automation.specs.androidtv

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
import eu.darken.sdmse.automation.core.common.idMatches
import eu.darken.sdmse.automation.core.common.pkgId
import eu.darken.sdmse.automation.core.common.scrollNode
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
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.progress.withProgress
import java.util.*
import javax.inject.Inject

@Reusable
open class AndroidTVSpecs @Inject constructor(
    private val ipcFunnel: IPCFunnel,
    @ApplicationContext private val context: Context,
    private val deviceDetective: DeviceDetective,
    private val androidTVLabels: AndroidTVLabels,
    private val settings: AppCleanerSettings,
) : ExplorerSpecGenerator() {

    override val label = TAG.toCaString()

    override val tag: String = TAG

    override suspend fun isResponsible(pkg: Installed): Boolean {
        if (settings.romTypeDetection.value() == SpecRomType.ANDROID_TV) return true
        return deviceDetective.isAndroidTV()
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
            val clearCacheButtonLabels = androidTVLabels.getClearCacheLabels(lang, script)

            val buttonFilter = fun(node: AccessibilityNodeInfo): Boolean {
                if (!node.idMatches("android:id/title")) return false
                return node.textMatchesAny(clearCacheButtonLabels)
            }

            val step = StepProcessor.Step(
                parentTag = TAG,
                label = "Find & click 'Clear Cache' (targets=$clearCacheButtonLabels)",
                windowIntent = CrawlerCommon.defaultWindowIntent(context, pkg),
                windowEventFilter = CrawlerCommon.defaultWindowFilter(SETTINGS_PKG),
                windowNodeTest = CrawlerCommon.windowCriteriaAppIdentifier(SETTINGS_PKG, ipcFunnel, pkg),
                nodeTest = buttonFilter,
                nodeRecovery = { it.scrollNode() },
                nodeMapping = CrawlerCommon.clickableParent(),
                action = getDefaultClearCacheClick(pkg, TAG)
            )
            stepper.withProgress(this) { process(step) }
        }
        run {
            val clearCacheTexts = androidTVLabels.getClearCacheLabels(lang, script)

            val windowCriteria = fun(node: AccessibilityNodeInfo): Boolean {
                if (node.pkgId != SETTINGS_PKG) return false
                return node.crawl().map { it.node }.any { subNode ->
                    when {
                        subNode.idMatches("com.android.tv.settings:id/guidance_title") -> when {
                            subNode.textMatchesAny(clearCacheTexts) -> true
                            subNode.textMatchesAny(clearCacheTexts.map { it.replace("?", "") }) -> true
                            subNode.textMatchesAny(clearCacheTexts.map { "$it?" }) -> true
                            else -> false
                        }

                        subNode.idMatches("com.android.tv.settings:id/guidance_container") -> true
                        else -> false
                    }
                }
            }

            val buttonFilter = fun(node: AccessibilityNodeInfo): Boolean {
                return when {
                    node.idMatches("com.android.tv.settings:id/guidedactions_item_content") -> true
                    node.idMatches("com.android.tv.settings:id/guidedactions_item_title") -> {
                        node.textMatchesAny(setOf(context.getString(android.R.string.ok)))
                    }

                    else -> false
                }
            }

            val step = StepProcessor.Step(
                parentTag = TAG,
                label = "Find & click 'OK' in confirmation dialog",
                windowNodeTest = windowCriteria,
                nodeTest = buttonFilter,
                nodeMapping = CrawlerCommon.clickableParent(),
                action = CrawlerCommon.defaultClick()
            )
            stepper.withProgress(this) { process(step) }
        }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: AndroidTVSpecs): SpecGenerator
    }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "AndroidTV", "Specs")
        val SETTINGS_PKG = "com.android.tv.settings".toPkgId()

    }
}
