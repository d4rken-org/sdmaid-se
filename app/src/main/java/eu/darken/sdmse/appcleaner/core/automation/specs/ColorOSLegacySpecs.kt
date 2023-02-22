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
import eu.darken.sdmse.automation.core.AutomationStepGenerator.Companion.getSysLocale
import eu.darken.sdmse.automation.core.crawler.AutomationCrawler
import eu.darken.sdmse.automation.core.crawler.CrawlerCommon
import eu.darken.sdmse.automation.core.crawler.isClickyButton
import eu.darken.sdmse.automation.core.crawler.textMatchesAny
import eu.darken.sdmse.common.DeviceDetective
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.toPkgId
import java.util.*
import javax.inject.Inject

@Reusable
open class ColorOSLegacySpecs @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ipcFunnel: IPCFunnel,
    private val deviceDetective: DeviceDetective,
    private val pkgRepo: PkgRepo,
) : AutomationStepGenerator {

    override val label = TAG

    // https://github.com/d4rken/sdmaid-public/issues/2910
    override suspend fun isResponsible(pkg: Installed): Boolean {
        if (deviceDetective.isCustomROM()) return false
        if (!deviceDetective.isOppo()) return false

        // ro.build.version.opporom=V3.2 full_oppo6763_17031-user 7.1.1 N6F26Q 1574188177 release-keys
        return hasApiLevel(26) && pkgRepo.isInstalled("com.coloros.simsettings".toPkgId())
    }

    private fun getClearCacheButtonLabels(lang: String, script: String): Collection<String> {
        context.get3rdPartyString(AOSPBaseSpecs.AOSP_SETTINGS_PKG, "clear_cache_btn_text")?.let {
            log(TAG) { "Using label from APK: $it" }
            return setOf(it)
        }
        return when {
            "en".toLang() == lang -> setOf("Clear Cache")
            "zh-Hans".toLoc().let { it.language == lang && it.script == script } -> setOf(
                // Simplified zh_CN
                "清除缓存"
            )
            "zh-Hant".toLoc().let { it.language == lang && it.script == script } -> setOf(
                // Traditional
                "清除快取"
            )
            "zh".toLang() == lang -> setOf(
                // OPPO/CPH1723/CPH1723:7.1.1/N6F26Q/1572534219:user/release-keys
                "清除缓存"
            )
            "ms".toLang() == lang -> setOf("Kosongkan cache") // ms_MY
            "cs".toLang() == lang -> setOf("Vymazat mezipaměť")
            "de".toLang() == lang -> setOf("Cache leeren")
            "es".toLang() == lang -> setOf("Borrar caché") // es_ES
            "fil".toLang() == lang -> setOf("I-clear ang cache") // fil_PH
            "fr".toLang() == lang -> setOf("Vider le cache")
            "in".toLang() == lang -> setOf("Hapus cache")
            "it".toLang() == lang -> setOf("Cancella cache")
            "sw".toLang() == lang -> setOf("Futa kashe")
            "hu".toLang() == lang -> setOf("Gyorsítótár törlése")
            "nl".toLang() == lang -> setOf("Cache wissen")
            "nb".toLang() == lang -> setOf("Tøm buffer")
            "pl".toLang() == lang -> setOf("Wyczyść pamięć")
            "pt".toLang() == lang -> setOf("Limpar cache") // pt_BR and pt_PT
            "ro".toLang() == lang -> setOf("Goliţi memoria cache")
            "sv".toLang() == lang -> setOf("Rensa cacheminne")
            "vi".toLang() == lang -> setOf("Xóa bộ nhớ cache")
            "tr".toLang() == lang -> setOf("Önbelleği temizle")
            "el".toLang() == lang -> setOf("Εκκαθάριση προσωρινής μνήμης")
            "kk".toLang() == lang -> setOf("Кэшті тазалау")
            "ru".toLang() == lang -> setOf(
                "Очистить кэш",
                "ОЧИСТИТЬ КЭШ"
            )
            "ur".toLang() == lang -> setOf("کیشے صاف کریں")
            "ar".toLang() == lang -> setOf("مسح التخزين المؤقت")
            "fa".toLang() == lang -> setOf("پاک کردن حافظهٔ پنهان")
            else -> throw UnsupportedOperationException()
        }
    }

    private val sourceClearCacheWindowNodeTest: suspend (Installed, Locale) -> (suspend (node: AccessibilityNodeInfo) -> Boolean) =
        { pkg: Installed, _: Locale ->
            CrawlerCommon.windowCriteriaAppIdentifier(AOSPBaseSpecs.AOSP_SETTINGS_PKG, ipcFunnel, pkg)
        }

    override suspend fun getSpecs(pkg: Installed): List<AutomationCrawler.Step> {
        val locale = getSysLocale()
        val lang = locale.language
        val script = locale.script

        log(TAG, VERBOSE) { "Getting specs for ${pkg.packageName} (lang=$lang, script=$script)" }

        val steps = mutableListOf<AutomationCrawler.Step>()

        run {
            // To this beforehand so we crash early if unsupported
            val clearCacheButtonLabels = getClearCacheButtonLabels(lang, script)
            val buttonFilter = fun(node: AccessibilityNodeInfo): Boolean {
                if (!node.isClickyButton()) return false
                return node.textMatchesAny(clearCacheButtonLabels)
            }

            steps.add(
                AutomationCrawler.Step(
                    parentTag = TAG,
                    pkgInfo = pkg,
                    label = "Find & click 'Clear Cache' (targets=$clearCacheButtonLabels)",
                    windowIntent = CrawlerCommon.defaultWindowIntent(context, pkg),
                    windowEventFilter = CrawlerCommon.defaultWindowFilter(AOSPBaseSpecs.AOSP_SETTINGS_PKG),
                    windowNodeTest = sourceClearCacheWindowNodeTest(pkg, locale),
                    nodeTest = buttonFilter,
                    nodeRecovery = CrawlerCommon.getDefaultNodeRecovery(pkg),
                    action = getDefaultClearCacheClick(pkg, TAG)
                )
            )
        }

        return steps
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: ColorOSLegacySpecs): AutomationStepGenerator
    }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "ColorOSLegacySpecs")
    }
}