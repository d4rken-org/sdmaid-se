package eu.darken.sdmse.appcleaner.core.automation.specs.coloros

import android.content.Context
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.automation.core.common.AutomationLabelSource
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.toPkgId
import javax.inject.Inject

@Reusable
class ColorOSLabels @Inject constructor(
    @ApplicationContext private val context: Context,
) : AutomationLabelSource {

    fun getStorageEntryLabel(): String? = context.get3rdPartyString(
        SETTINGS_PKG,
        "storage_use"
    ).also { log(TAG) { "getStorageEntryLabel(): $it" } }

    fun getStorageEntryLabels(lang: String, script: String) = when {
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

    fun getClearCacheLabel(): String? = context.get3rdPartyString(
        SETTINGS_PKG,
        "clear_cache_btn_text"
    ).also { log(TAG) { "getClearCacheLabel(): $it" } }

    fun getClearCacheLabels(lang: String, script: String): Collection<String> = when {
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

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "ColorOS", "Labels")
        val SETTINGS_PKG = "com.android.settings".toPkgId()
    }
}