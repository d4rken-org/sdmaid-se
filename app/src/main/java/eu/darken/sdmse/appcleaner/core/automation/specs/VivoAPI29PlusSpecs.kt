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
class VivoAPI29PlusSpecs @Inject constructor(
    private val ipcFunnel: IPCFunnel,
    @ApplicationContext private val context: Context,
    private val deviceDetective: DeviceDetective,
) : AOSP29PlusSpecs(ipcFunnel, context, deviceDetective) {

    override val label = TAG

    init {
        logTag = TAG
    }

    override suspend fun isResponsible(pkg: Installed): Boolean {
        if (deviceDetective.isCustomROM()) return false
        if (!hasApiLevel(29)) return false
        return deviceDetective.isVivo()
    }

    // 10: className=android.widget.LinearLayout, text=null, isClickable=true, isEnabled=true, viewIdResourceName=null, pkgName=com.android.settings
    // 14: className=android.widget.TextView, text=Internal storage, isClickable=false, isEnabled=true, viewIdResourceName=android:id/title, pkgName=com.android.settings
    @Suppress("IntroduceWhenSubject")
    override fun getStorageEntryLabels(lang: String, script: String): Collection<String> = when {
        "en".toLang() == lang -> setOf(
            // https://github.com/d4rken/sdmaid-public/issues/4487
            "Internal storage",
            // https://github.com/d4rken/sdmaid-public/issues/5045
            // vivo/2037M/2037:11/RP1A.200720.012/compiler0720222823:user/release-keys
            "Storage & cache"
        )
        // https://github.com/d4rken/sdmaid-public/issues/4758
        "in".toLang() == lang -> setOf("Penyimpanan & cache")
        else -> emptyList()
    }.tryAppend { super.getStorageEntryLabels(lang, script) }

    /**
     * 10: className=android.widget.LinearLayout, text=null, isClickable=true, isEnabled=true, viewIdResourceName=null, pkgName=com.android.settings
     * -11: className=android.widget.RelativeLayout, text=null, isClickable=false, isEnabled=true, viewIdResourceName=null, pkgName=com.android.settings
     * --12: className=android.widget.RelativeLayout, text=null, isClickable=false, isEnabled=true, viewIdResourceName=null, pkgName=com.android.settings
     * ---13: className=android.widget.LinearLayout, text=null, isClickable=false, isEnabled=true, viewIdResourceName=vivo:id/titleLayout, pkgName=com.android.settings
     * ----14: className=android.widget.TextView, text=Internal storage, isClickable=false, isEnabled=true, viewIdResourceName=android:id/title, pkgName=com.android.settings
     * ---13: className=android.widget.TextView, text=183 MB have been used., isClickable=false, isEnabled=true, viewIdResourceName=android:id/summary, pkgName=com.android.settings
     * --12: className=android.widget.LinearLayout, text=null, isClickable=false, isEnabled=true, viewIdResourceName=android:id/widget_frame, pkgName=com.android.settings
     * ---13: className=android.widget.ImageView, text=null, isClickable=false, isEnabled=true, viewIdResourceName=null, pkgName=com.android.settings
     **/
    override suspend fun getStorageEntrySpec(
        pkg: Installed,
        locale: Locale,
        lang: String,
        script: String
    ): AutomationCrawler.Step {
        val storageEntryLabels = try {
            getStorageEntryLabels(lang, script)
        } catch (e: UnsupportedOperationException) {
            log(TAG, WARN) { "Constellation is unsupported, trying English..." }
            getStorageEntryLabels("en", "")
        }

        val storageFilter = fun(node: AccessibilityNodeInfo): Boolean {
            if (!node.isTextView() || !node.idContains("android:id/title")) return false
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
            nodeMapping = CrawlerCommon.clickableParent(maxNesting = 4),
            action = CrawlerCommon.defaultClick()
        )
    }

    override suspend fun getClearCacheEntrySpec(
        pkg: Installed,
        locale: Locale,
        lang: String,
        script: String
    ): AutomationCrawler.Step {
        val clearCacheButtonLabels = try {
            getClearCacheButtonLabels(lang, script)
        } catch (e: UnsupportedOperationException) {
            log(TAG, WARN) { "Constellation is unsupported, trying English..." }
            getClearCacheButtonLabels("en", "")
        }

        // 14: className=android.widget.TextView, text=Clear cache, isClickable=true, isEnabled=true, viewIdResourceName=com.android.settings:id/button, pkgName=com.android.settings
        val buttonFilter = fun(node: AccessibilityNodeInfo): Boolean {
            if (!node.isClickable) return false
            return node.textMatchesAny(clearCacheButtonLabels)
        }


        return AutomationCrawler.Step(
            parentTag = logTag,
            pkgInfo = pkg,
            label = "Find & click 'Clear Cache' (targets=$clearCacheButtonLabels)",
            windowNodeTest = sourceClearCacheWindowNodeTest(pkg, locale),
            nodeTest = buttonFilter,
            action = getDefaultClearCacheClick(pkg, logTag)
        )
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: VivoAPI29PlusSpecs): AutomationStepGenerator
    }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "VivoAPI29PlusSpecs")
    }
}