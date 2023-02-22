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
import eu.darken.sdmse.automation.core.crawler.idContains
import eu.darken.sdmse.automation.core.crawler.textMatchesAny
import eu.darken.sdmse.common.DeviceDetective
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.toPkgId
import java.util.*
import javax.inject.Inject

@Reusable
class FlymeSpecs @Inject constructor(
    private val ipcFunnel: IPCFunnel,
    @ApplicationContext private val context: Context,
    private val deviceDetective: DeviceDetective,
    private val pkgRepo: PkgRepo,
) : AutomationStepGenerator {

    override val label = TAG

    // https://github.com/d4rken/sdmaid-public/issues/2910
    override suspend fun isResponsible(pkg: Installed): Boolean {
        if (deviceDetective.isCustomROM()) return false
        if (!deviceDetective.isMeizu()) return false

        // Meizu/meizu_M8/meizuM8:8.1.0/O11019/1540458380:user/release-keys
        return pkgRepo.isInstalled("com.meizu.flyme.update".toPkgId())
    }

    // Taken from AOSP14to28
    private fun getClearCacheButtonLabels(lang: String, script: String): Collection<String> {
        context.get3rdPartyString(AOSPBaseSpecs.AOSP_SETTINGS_PKG, "clear_cache_btn_text")?.let {
            log(TAG) { "Using label from APK: $it" }
            return setOf(it)
        }
        return when {
            "de".toLang() == lang -> setOf(
                "Cache leeren",
                "CACHE LÖSCHEN"
            )
            "en".toLang() == lang -> setOf("Clear cache")
            "cs".toLang() == lang -> setOf("VYMAZAT MEZIPAMĚŤ")
            "ru".toLang() == lang -> setOf(
                "Очистить кеш",
                "ОЧИСТИТЬ КЭШ"
            )
            "es".toLang() == lang -> setOf(
                "BORRAR CACHÉ",
                "BORRAR MEMORIA CACHÉ",
                "ELIMINAR CACHÉ"
            )
            "zh-Hans".toLoc().let { it.language == lang && it.script == script } -> setOf("清除缓存")
            "zh-Hant".toLoc().let { it.language == lang && it.script == script } -> setOf(
                "清除快取",
                "清除快取資料"
            )
            "zh".toLang() == lang -> setOf("清除缓存")
            "ja".toLang() == lang -> setOf("キャッシュを削除")
            "pt".toLang() == lang -> setOf("LIMPAR CACHE")
            "in".toLang() == lang -> setOf("Hapus cache")
            "hi".toLang() == lang -> setOf("कैश साफ़ करें")
            "it".toLang() == lang -> setOf(
                "Svuota cache",
                "CANCELLA CACHE"
            )
            "uk".toLang() == lang -> setOf("Очистити кеш")
            "fr".toLang() == lang -> setOf(
                "Vider le cache",
                "EFFACER LE CACHE"
            )
            "tr".toLang() == lang -> setOf("Önbelleği temizle")
            "kr".toLang() == lang -> setOf("캐시 지우기")
            "pl".toLang() == lang -> setOf("Wyczyść pamięć podręczną")
            "vi".toLang() == lang -> setOf(
                "Xóa bộ nhớ đệm",
                "Xóa bộ đệm"
            )
            "el".toLang() == lang -> setOf("Διαγραφή προσωρινής μνήμης")
            "nl".toLang() == lang -> setOf("Cache wissen")
            "hu".toLang() == lang -> setOf("A gyorsítótár törlése")
            "ko".toLang() == lang -> setOf(
                "캐시 지우기",
                "캐시 삭제"
            )
            "sl".toLang() == lang -> setOf("Zbriši medpomnilnik")
            "th".toLang() == lang -> setOf("ล้างแคช")
            "iw".toLang() == lang -> setOf("נקה מטמון")
            "ml".toLang() == lang -> setOf(
                // ml_IN @AOSP
                "കാഷെ മായ്ക്കുക"
            )
            "fi".toLang() == lang -> setOf("Tyhjennä välimuisti")
            "ar".toLang() == lang -> setOf(
                // ar_EG @ AOSP
                "محو ذاكرة التخزين المؤقت"
            )
            "nb".toLang() == lang -> setOf("TØM BUFFEREN")
            "bg".toLang() == lang -> setOf("ИЗЧИСТВАНЕ НА КЕША")
            "sk".toLang() == lang -> setOf("VYMAZAŤ VYROVNÁVACIU PAMÄŤ")
            "ms".toLang() == lang -> setOf(
                // ROM is not completely translated (AOSP API 27)
                "Clear cache"
            )
            "lt".toLang() == lang -> setOf("IŠVALYTI TALPYKLĄ")
            "sv".toLang() == lang -> setOf("RENSA CACHEMINNE")
            "sr".toLang() == lang -> setOf(
                "Обриши кеш",
                // samsung/beyond1ltexx/beyond1:9/PPR1.180610.011/G973FXXU3ASIG:user/release-keys/en_US
                "Obriši keš memoriju"
            )
            "da".toLang() == lang -> setOf("Ryd cache")
            "ca".toLang() == lang -> setOf("Esborra la memòria cau")
            "fa".toLang() == lang -> setOf("پاک کردن حافظهٔ پنهان")
            "et".toLang() == lang -> setOf("Tühjenda vahemälu")
            "ro".toLang() == lang -> setOf("Goliți memoria cache")
            "hr".toLang() == lang -> setOf("Očisti predmemoriju")
            "bn".toLang() == lang -> setOf("ক্যাশে সাফ করুন")
            "lv".toLang() == lang -> setOf("Notīrīt kešatmiņu")
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
            // Do this beforehand so we crash early if unsupported
            val clearCacheButtonLabels = getClearCacheButtonLabels(lang, script)
            val buttonFilter = fun(node: AccessibilityNodeInfo): Boolean {
                if (!node.isClickable) return false
                // viewIdResName: com.android.settings:id/right_text
                if (!node.idContains("right_text")) return false
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
        @Binds @IntoSet abstract fun mod(mod: FlymeSpecs): AutomationStepGenerator
    }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "FlymeSpecs")
    }
}