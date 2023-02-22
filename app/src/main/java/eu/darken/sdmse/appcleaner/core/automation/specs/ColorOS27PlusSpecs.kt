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
import eu.darken.sdmse.automation.core.crawler.*
import eu.darken.sdmse.common.DeviceDetective
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
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
class ColorOS27PlusSpecs @Inject constructor(
    private val ipcFunnel: IPCFunnel,
    @ApplicationContext private val context: Context,
    private val deviceDetective: DeviceDetective,
    private val pkgRepo: PkgRepo,
) : ColorOSLegacySpecs(context, ipcFunnel, deviceDetective, pkgRepo) {

    override val label = TAG

    override suspend fun isResponsible(pkg: Installed): Boolean {
        if (deviceDetective.isCustomROM()) return false
        if (!deviceDetective.isOppo()) return false

        return hasApiLevel(26) && hasColorApps()
    }

    private suspend fun hasColorApps(): Boolean = listOf(
        // Not available on OPPO/CPH2247EEA/OP4F7FL1:11/RKQ1.201105.002/1632415665086:user/release-keys
        "com.coloros.simsettings",
        "com.coloros.filemanager"
    ).any { pkgRepo.isInstalled(it.toPkgId()) }


    private fun getStorageEntryLabels(lang: String, script: String): Collection<String> {
        context.get3rdPartyString(AOSPBaseSpecs.AOSP_SETTINGS_PKG, "storage_use")?.let {
            log(TAG) { "Using label from APK: $it" }
            return setOf(it)
        }
        return when {
            "en".toLang() == lang -> setOf("Storage Usage", "Storage usage")
            "de".toLang() == lang -> setOf("Speichernutzung")
            "it".toLang() == lang -> setOf("Utilizzo memoria")
            "in".toLang() == lang -> setOf("Penggunaan penyimpanan")
            "nl".toLang() == lang -> setOf("Opslaggebruik")
            "zh-Hans".toLoc().let { it.language == lang && it.script == script } -> setOf(
                // Simplified zh_CN
                "存储占用"
            )
            "zh".toLang() == lang -> setOf(
                "存储占用"
            )
            "ja".toLang() == lang -> setOf("ストレージ使用状況")
            "ka".toLang() == lang -> setOf("მეხსიერება")
            // A52 (CPH2069) @ Android 10
            "ru".toLang() == lang -> setOf("Использование памяти")
            // F7 (CPH1819) @ Android 10
            "th".toLang() == lang -> setOf("การใช้เนื้อที่เก็บข้อมูล")
            // Reno2 Z (CPH1951)
            "pl".toLang() == lang -> setOf("Użycie pamięci")
            // CPH2113
            "ar".toLang() == lang -> setOf("استخدام سعة التخزين")
            "es".toLang() == lang -> setOf("Uso de almacenamiento")
            "tr".toLang() == lang -> setOf("Saklama alanı kullanımı")
            "fr".toLang() == lang -> setOf("Utilisation du stockage")
            "vi".toLang() == lang -> setOf("Sử dụng lưu trữ")
            "ms".toLang() == lang -> setOf("Penggunaan storan")
            else -> throw UnsupportedOperationException()
        }
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
            "th".toLang() == lang -> setOf("ล้างแคช")
            "ja".toLang() == lang -> setOf("キャッシュを消去")
            else -> throw UnsupportedOperationException()
        }
    }

    private val sourceClearCacheWindowNodeTest: suspend (Installed, Locale) -> (suspend (node: AccessibilityNodeInfo) -> Boolean) =
        { pkg: Installed, _: Locale ->
            val recognizesName =
                CrawlerCommon.windowCriteriaAppIdentifier(AOSPBaseSpecs.AOSP_SETTINGS_PKG, ipcFunnel, pkg)
            // https://github.com/d4rken/sdmaid-public/issues/4939
            val hasClearCacheButton = CrawlerCommon.windowCriteria(AOSPBaseSpecs.AOSP_SETTINGS_PKG) { node ->
                node.crawl().map { it.node }
                    .any { toTest -> toTest.idMatches("com.android.settings:id/clear_cache_button") }
            }
            val combined: suspend (AccessibilityNodeInfo) -> Boolean = { node: AccessibilityNodeInfo ->
                recognizesName.invoke(node) || hasClearCacheButton.invoke(node)
            }
            combined
        }

    override suspend fun getSpecs(pkg: Installed): List<AutomationCrawler.Step> {
        val locale = getSysLocale()
        val lang = locale.language
        val script = locale.script
        log(TAG, VERBOSE) { "Getting specs for ${pkg.packageName} (lang=$lang, script=$script)" }

        val steps = mutableListOf<AutomationCrawler.Step>()

        run {
            /**
             * 13: className=android.widget.LinearLayout, text=null, isClickable=true, isEnabled=true, viewIdResourceName=null, pkgName=com.android.settings
             *  14: className=android.widget.RelativeLayout, text=null, isClickable=false, isEnabled=true, viewIdResourceName=null, pkgName=com.android.settings
             *   15: className=android.widget.TextView, text=Storage Usage, isClickable=false, isEnabled=true, viewIdResourceName=android:id/title, pkgName=com.android.settings
             *   15: className=android.widget.TextView, text=715 MB, isClickable=false, isEnabled=true, viewIdResourceName=android:id/summary, pkgName=com.android.settings
             *  14: className=android.widget.LinearLayout, text=null, isClickable=false, isEnabled=true, viewIdResourceName=android:id/widget_frame, pkgName=com.android.settings
             *   15: className=android.widget.LinearLayout, text=null, isClickable=false, isEnabled=true, viewIdResourceName=null, pkgName=com.android.settings
             *    16: className=android.widget.LinearLayout, text=null, isClickable=false, isEnabled=true, viewIdResourceName=null, pkgName=com.android.settings
             *    16: className=android.widget.ImageView, text=null, isClickable=false, isEnabled=true, viewIdResourceName=com.android.settings:id/color_preference_widget_jump, pkgName=com.android.settings
             */

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

            steps.add(
                AutomationCrawler.Step(
                    parentTag = TAG,
                    pkgInfo = pkg,
                    label = "Find & click 'Storage Usage' (targets=$storageEntryLabels)",
                    windowIntent = CrawlerCommon.defaultWindowIntent(context, pkg),
                    windowEventFilter = CrawlerCommon.defaultWindowFilter(AOSPBaseSpecs.AOSP_SETTINGS_PKG),
                    windowNodeTest = CrawlerCommon.windowCriteriaAppIdentifier(
                        AOSPBaseSpecs.AOSP_SETTINGS_PKG,
                        ipcFunnel,
                        pkg
                    ),
                    nodeTest = storageFilter,
                    nodeRecovery = CrawlerCommon.getDefaultNodeRecovery(pkg),
                    nodeMapping = CrawlerCommon.clickableParent(),
                    action = CrawlerCommon.defaultClick()
                )
            )
        }

        run {
            // 16: className=android.widget.Button, text=Clear Cache, isClickable=true, isEnabled=true, viewIdResourceName=com.android.settings:id/button, pkgName=com.android.settings

            val clearCacheButtonLabels = try {
                getClearCacheButtonLabels(lang, script)
            } catch (e: UnsupportedOperationException) {
                log(TAG, WARN) { "Constellation is unsupported, trying English..." }
                getClearCacheButtonLabels("en", "")
            }
            val buttonFilter = fun(node: AccessibilityNodeInfo): Boolean {
                return node.textMatchesAny(clearCacheButtonLabels)
            }
            steps.add(
                AutomationCrawler.Step(
                    parentTag = TAG,
                    pkgInfo = pkg,
                    label = "Find & click 'Clear Cache' (targets=$clearCacheButtonLabels)",
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
        @Binds @IntoSet abstract fun mod(mod: ColorOS27PlusSpecs): AutomationStepGenerator
    }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "ColorOS27PlusSpecs")
    }
}