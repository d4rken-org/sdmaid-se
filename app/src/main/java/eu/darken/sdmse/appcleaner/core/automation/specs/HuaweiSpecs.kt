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
class HuaweiSpecs @Inject constructor(
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
        return hasApiLevel(29) && deviceDetective.isHuawei()
    }

    @Suppress("IntroduceWhenSubject")
    override fun getStorageEntryLabels(lang: String, script: String): Collection<String> {
        context.get3rdPartyString(AOSP_SETTINGS_PKG, "storage_settings")?.let {
            log(TAG) { "Using label from APK: $it" }
            return setOf(it)
        }

        return when {
            "en".toLang() == lang -> setOf(
                // https://github.com/d4rken/sdmaid-public/issues/3576
                // HONOR/PCT-L29RU/HWPCT:10/HUAWEIPCT-L29/10.0.0.195C10:user/release-keys
                "Storage"
            ).tryPrepend { super.getStorageEntryLabels(lang, script) }
            "ru".toLang() == lang -> setOf(
                // https://github.com/d4rken/sdmaid-public/issues/3576
                // HONOR/PCT-L29RU/HWPCT:10/HUAWEIPCT-L29/10.0.0.195C10:user/release-keys
                "Память"
            ).tryPrepend { super.getStorageEntryLabels(lang, script) }
            "de".toLang() == lang -> setOf(
                // HUAWEI/VOG-L29EEA/HWVOG:10/HUAWEIVOG-L29/10.0.0.168C431:user/release-keys
                "Speicher"
            )
            "pl".toLang() == lang -> setOf(
                // HUAWEI/VOG-L29EEA/HWVOG:10/HUAWEIVOG-L29/10.0.0.178C431:user/release-keys
                "Pamięć"
            )
            "it".toLang() == lang -> setOf(
                "Spazio di archiviazione e cache",
                // HONOR/JSN-L21/HWJSN-H:10/HONORJSN-L21/10.0.0.175C432:user/release-keys
                "Memoria"
            )
            "ca".toLang() == lang -> setOf(
                // EMUI 11 (Android 10) [Huawei Mate 20 Pro]
                "Emmagatzematge"
            )
            else -> emptySet()
        }.tryAppend { super.getStorageEntryLabels(lang, script) }
    }

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
            nodeMapping = CrawlerCommon.clickableParent(maxNesting = 7),
            action = CrawlerCommon.defaultClick()
        )
    }

    @Suppress("IntroduceWhenSubject")
    override fun getClearCacheButtonLabels(lang: String, script: String): Collection<String> {
        context.get3rdPartyString(AOSP_SETTINGS_PKG, "clear_cache_btn_text")?.let {
            log(TAG) { "Using label from APK: $it" }
            return setOf(it)
        }
        return when {
            "ca".toLang() == lang -> setOf(
                // EMUI 11 (Android 10) [Huawei Mate 20 Pro]
                "Esborrar la memòria cau"
            )
            else -> emptySet()
        }.tryAppend { super.getClearCacheButtonLabels(lang, script) }
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

        val buttonFilter = fun(node: AccessibilityNodeInfo): Boolean {
            if (!node.isClickyButton()) return false
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
        @Binds @IntoSet abstract fun mod(mod: HuaweiSpecs): AutomationStepGenerator
    }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "HuaweiSpecs")
    }
}