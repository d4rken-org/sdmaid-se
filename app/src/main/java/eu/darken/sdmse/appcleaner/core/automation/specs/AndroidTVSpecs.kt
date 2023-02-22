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
import eu.darken.sdmse.automation.core.pkgId
import eu.darken.sdmse.common.DeviceDetective
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.toPkgId
import java.util.*
import javax.inject.Inject

@Reusable
open class AndroidTVSpecs @Inject constructor(
    private val ipcFunnel: IPCFunnel,
    @ApplicationContext private val context: Context,
    private val deviceDetective: DeviceDetective,
) : AutomationStepGenerator {

    override val label = TAG

    override suspend fun isResponsible(pkg: Installed): Boolean = deviceDetective.isAndroidTV()

    // Taken from AOSP14to28Specs
    private fun getClearCacheButtonLabels(lang: String, script: String): Collection<String> = when {
        "de".toLang() == lang -> setOf("Cache leeren", "CACHE LÖSCHEN")
        "en".toLang() == lang -> setOf("Clear cache")
        "cs".toLang() == lang -> setOf("VYMAZAT MEZIPAMĚŤ")
        "ru".toLang() == lang -> setOf("Очистить кеш", "ОЧИСТИТЬ КЭШ")
        "es".toLang() == lang -> setOf(
            "BORRAR CACHÉ",
            "BORRAR MEMORIA CACHÉ",
            "ELIMINAR CACHÉ",
            "ELIMINAR MEMORIA CACHÉ"
        )
        "zh-Hans".toLoc().let { it.language == lang && it.script == script } -> setOf(
            // Simplified
            "清除缓存"
        )
        "zh-Hant".toLoc().let { it.language == lang && it.script == script } -> setOf(
            // Traditional
            "清除快取",
            "清除快取資料"
        )
        "zh".toLang() == lang -> setOf("清除缓存")
        "ja".toLang() == lang -> setOf("キャッシュを削除")
        "pt".toLang() == lang -> setOf("LIMPAR CACHE")
        "in".toLang() == lang -> setOf("Hapus cache")
        "hi".toLang() == lang -> setOf("कैश साफ़ करें")
        "it".toLang() == lang -> setOf("Svuota cache", "CANCELLA CACHE")
        "uk".toLang() == lang -> setOf("Очистити кеш")
        "fr".toLang() == lang -> setOf("Vider le cache", "EFFACER LE CACHE")
        "tr".toLang() == lang -> setOf("Önbelleği temizle")
        "kr".toLang() == lang -> setOf("캐시 지우기")
        "pl".toLang() == lang -> setOf("Wyczyść pamięć podręczną")
        "vi".toLang() == lang -> setOf("Xóa bộ nhớ đệm", "Xóa bộ đệm")
        "el".toLang() == lang -> setOf("Διαγραφή προσωρινής μνήμης")
        "nl".toLang() == lang -> setOf("Cache wissen")
        "hu".toLang() == lang -> setOf("A gyorsítótár törlése")
        "ko".toLang() == lang -> setOf("캐시 지우기", "캐시 삭제")
        "sl".toLang() == lang -> setOf("Zbriši medpomnilnik")
        "th".toLang() == lang -> setOf("ล้างแคช")
        "iw".toLang() == lang -> setOf("נקה מטמון")
        "ml".toLang() == lang -> setOf("കാഷെ മായ്ക്കുക")
        "fi".toLang() == lang -> setOf("Tyhjennä välimuisti")
        "ar".toLang() == lang -> setOf("محو ذاكرة التخزين المؤقت")
        "nb".toLang() == lang -> setOf("TØM BUFFEREN")
        "bg".toLang() == lang -> setOf("ИЗЧИСТВАНЕ НА КЕША")
        "sk".toLang() == lang -> setOf("VYMAZAŤ VYROVNÁVACIU PAMÄŤ")
        "ms".toLang() == lang -> setOf("Clear cache")
        "lt".toLang() == lang -> setOf("IŠVALYTI TALPYKLĄ")
        "sv".toLang() == lang -> setOf("RENSA CACHEMINNE")
        "sr".toLang() == lang -> setOf("Обриши кеш", "Obriši keš memoriju")
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

    private suspend fun getClearCacheEntrySpec(
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
            if (!node.idMatches("android:id/title")) return false
            return node.textMatchesAny(clearCacheButtonLabels)
        }

        return AutomationCrawler.Step(
            parentTag = TAG,
            pkgInfo = pkg,
            label = "Find & click 'Clear Cache' (targets=$clearCacheButtonLabels)",
            windowIntent = CrawlerCommon.defaultWindowIntent(context, pkg),
            windowEventFilter = CrawlerCommon.defaultWindowFilter(ANDROID_TV_SETTINGS_PKG),
            windowNodeTest = CrawlerCommon.windowCriteriaAppIdentifier(
                ANDROID_TV_SETTINGS_PKG,
                ipcFunnel,
                pkg
            ),
            nodeTest = buttonFilter,
            nodeRecovery = { it.scrollNode() },
            nodeMapping = CrawlerCommon.clickableParent(),
            action = getDefaultClearCacheClick(pkg, TAG)
        )
    }

    private fun getConfirmationButtonSpec(
        pkg: Installed,
        locale: Locale,
        lang: String,
        script: String
    ): AutomationCrawler.Step {
        val clearCacheTexts = try {
            getClearCacheButtonLabels(lang, script)
        } catch (e: UnsupportedOperationException) {
            log(TAG, WARN) { "Constellation is unsupported, trying English..." }
            getClearCacheButtonLabels("en", "")
        }

        val windowCriteria = fun(node: AccessibilityNodeInfo): Boolean {
            if (node.pkgId != ANDROID_TV_SETTINGS_PKG) return false
            return node.crawl().map { it.node }.any { subNode ->
                if (!subNode.idMatches("com.android.tv.settings:id/guidance_title")) return@any false
                return@any when {
                    subNode.textMatchesAny(clearCacheTexts) -> true
                    subNode.textMatchesAny(clearCacheTexts.map { it.replace("?", "") }) -> true
                    subNode.textMatchesAny(clearCacheTexts.map { "$it?" }) -> true
                    else -> false
                }
            }
        }

        val buttonFilter = fun(node: AccessibilityNodeInfo): Boolean {
            return node.idMatches("com.android.tv.settings:id/guidedactions_item_content")
        }

        return AutomationCrawler.Step(
            parentTag = TAG,
            pkgInfo = pkg,
            label = "Find & click 'OK' in confirmation dialog",
            windowNodeTest = windowCriteria,
            nodeTest = buttonFilter,
            nodeMapping = CrawlerCommon.clickableParent(),
            action = CrawlerCommon.defaultClick()
        )
    }

    override suspend fun getSpecs(pkg: Installed): List<AutomationCrawler.Step> {
        val locale = AutomationStepGenerator.getSysLocale()
        val lang = locale.language
        val script = locale.script

        log(TAG) { "Getting specs for ${pkg.packageName} (lang=$lang, script=$script)" }

        return listOf(
            getClearCacheEntrySpec(pkg, locale, lang, script),
            getConfirmationButtonSpec(pkg, locale, lang, script)
        )
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: AndroidTVSpecs): AutomationStepGenerator
    }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "AndroidTVSpecs")
        val ANDROID_TV_SETTINGS_PKG = "com.android.tv.settings".toPkgId()

    }
}
