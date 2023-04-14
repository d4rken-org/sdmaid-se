package eu.darken.sdmse.appcleaner.core.automation.specs

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.automation.core.AutomationStepGenerator
import eu.darken.sdmse.automation.core.crawler.*
import eu.darken.sdmse.common.BuildWrap
import eu.darken.sdmse.common.DeviceDetective
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.features.Installed
import java.util.*
import javax.inject.Inject

@Reusable
open class AOSP34PlusSpecs @Inject constructor(
    private val ipcFunnel: IPCFunnel,
    @ApplicationContext private val context: Context,
    private val deviceDetective: DeviceDetective,
) : AOSP29PlusSpecs(ipcFunnel, context, deviceDetective) {

    override val label = TAG

    init {
        logTag = TAG
    }

    override suspend fun isResponsible(pkg: Installed): Boolean =
        (hasApiLevel(34) || hasApiLevel(33) && BuildWrap.VERSION.PREVIEW_SDK_INT != 0) && !deviceDetective.isAndroidTV()

    override suspend fun getStorageEntrySpec(
        pkg: Installed,
        locale: Locale,
        lang: String,
        script: String
    ): AutomationCrawler.Step {
        val storageEntryLabels = try {
            getStorageEntryLabels(lang, script)
        } catch (e: UnsupportedOperationException) {
            log(WARN) { "Constellation is unsupported, trying English..." }
            getStorageEntryLabels("en", "")
        }

        val storageFilter = fun(node: AccessibilityNodeInfo): Boolean {
            if (!node.isTextView()) return false
            return node.textMatchesAny(storageEntryLabels)
        }

        return AutomationCrawler.Step(
            parentTag = logTag,
            pkgInfo = pkg,
            label = "Find & click 'Storage' (targets=$storageEntryLabels)",
            windowIntent = CrawlerCommon.defaultWindowIntent(context, pkg),
            windowEventFilter = CrawlerCommon.defaultWindowFilter(AOSP_SETTINGS_PKG),
            windowNodeTest = CrawlerCommon.windowCriteriaAppIdentifier(AOSP_SETTINGS_PKG, ipcFunnel, pkg),
            nodeTest = storageFilter,
            nodeRecovery = CrawlerCommon.getDefaultNodeRecovery(pkg),
            nodeMapping = CrawlerCommon.clickableParent(),
            action = CrawlerCommon.defaultClick()
        )
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: AOSP34PlusSpecs): AutomationStepGenerator
    }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "AOSP34PlusSpecs")
    }

}
