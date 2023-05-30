package eu.darken.sdmse.appcleaner.core.automation.specs.flyme

import android.content.Context
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.automation.core.common.AutomationLabelSource
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.toPkgId
import javax.inject.Inject

@Reusable
class FlymeLabels @Inject constructor(
    @ApplicationContext private val context: Context,
) : AutomationLabelSource {

    fun getClearCacheLabel(): String? = context.get3rdPartyString(
        SETTINGS_PKG,
        "clear_cache_btn_text"
    ).also { log(TAG) { "getClearCacheLabel(): $it" } }

    // Taken from AOSP14to28
    fun getClearCacheLabels(lang: String, script: String): Collection<String> = when {
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

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "Flyme", "Labels")
        val SETTINGS_PKG = "com.android.settings".toPkgId()
    }
}