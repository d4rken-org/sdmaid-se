package eu.darken.sdmse.appcleaner.core.automation.specs

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import com.squareup.moshi.JsonClass
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.appcleaner.core.AppCleanerSettings
import eu.darken.sdmse.automation.core.AutomationStepGenerator
import eu.darken.sdmse.automation.core.AutomationStepGenerator.Companion.getSysLocale
import eu.darken.sdmse.automation.core.crawler.AutomationCrawler
import eu.darken.sdmse.automation.core.crawler.CrawlerCommon
import eu.darken.sdmse.automation.core.crawler.textMatchesAny
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.toPkgId
import javax.inject.Inject

class CustomSpecs @Inject constructor(
    private val ipcFunnel: IPCFunnel,
    @ApplicationContext private val context: Context,
    private val appCleanerSettings: AppCleanerSettings,
) : AutomationStepGenerator {

    override val label: String = "Custom spec"

    override suspend fun isResponsible(pkg: Installed): Boolean {
        return appCleanerSettings.automationCustomSteps.value() != null
    }

    override suspend fun getSpecs(pkg: Installed): List<AutomationCrawler.Step> {
        val locale = getSysLocale()
        val lang = locale.language
        val script = locale.script

        log(TAG, VERBOSE) { "Getting specs for ${pkg.packageName} (lang=$lang, script=$script)" }

        throw NotImplementedError()
    }

    private suspend fun buildCustomSpecEntry(pkg: Installed, index: Int, target: String): AutomationCrawler.Step {
        val targetFilter = fun(node: AccessibilityNodeInfo): Boolean {
            return node.textMatchesAny(listOf(target))
        }

        return when (index) {
            0 -> AutomationCrawler.Step(
                parentTag = TAG,
                pkgInfo = pkg,
                label = "Find & click '$target'",
                windowIntent = CrawlerCommon.defaultWindowIntent(context, pkg),
                windowEventFilter = CrawlerCommon.defaultWindowFilter(AOSP_SETTINGS_PKG),
                windowNodeTest = CrawlerCommon.windowCriteriaAppIdentifier(AOSP_SETTINGS_PKG, ipcFunnel, pkg),
                nodeTest = targetFilter,
                nodeRecovery = CrawlerCommon.getDefaultNodeRecovery(pkg),
                nodeMapping = CrawlerCommon.clickableParent(maxNesting = 6),
                action = CrawlerCommon.defaultClick()
            )
            else -> AutomationCrawler.Step(
                parentTag = TAG,
                pkgInfo = pkg,
                label = "Find & click '$target'",
                nodeTest = targetFilter,
                nodeRecovery = CrawlerCommon.getDefaultNodeRecovery(pkg),
                nodeMapping = CrawlerCommon.clickableParent(maxNesting = 6),
                action = CrawlerCommon.defaultClick()
            )
        }
    }

    @JsonClass(generateAdapter = true)
    data class Config(
        val test: String
    )

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: CustomSpecs): AutomationStepGenerator
    }

    companion object {
        val TAG = logTag("AppCleaner", "Automation", "CustomSpecs")
        val AOSP_SETTINGS_PKG = "com.android.settings".toPkgId()
    }
}