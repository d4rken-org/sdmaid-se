package eu.darken.sdmse.appcleaner.core.automation.specs.aosp

import android.content.Context
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.automation.core.common.AutomationLabelSource
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.toPkgId
import javax.inject.Inject

@Reusable
class AOSPLabels14Plus @Inject constructor(
    @ApplicationContext private val context: Context,
) : AutomationLabelSource {

    fun getStorageEntryDynamic(): Set<String>? = context
        .get3rdPartyString(SETTINGS_PKG, "storage_settings")
        .also { log(TAG) { "getStorageEntryDynamic(): $it" } }
        ?.let { setOf(it) }

    // https://github.com/aosp-mirror/platform_packages_apps_settings/tree/pie-platform-release/res
    // storage_settings
    fun getStorageEntryStatic(lang: String, script: String): Set<String> = when {
        "de".toLang() == lang -> setOf(
            "Speicher"
        )

        "en".toLang() == lang -> setOf(
            "Storage",
            // Nokia 3.1 @ Stock Android One 9
            // https://github.com/d4rken/sdmaid-public/issues/2695
            "Storage space",
            // https://github.com/d4rken/sdmaid-public/issues/4046
            // asus/WW_Phone/ASUS_X00IDB:8.1.0/OPM1.171019.011/15.2016.1907.519-0:user/release-keys
            "Storage & memory"
        )

        "cs".toLang() == lang -> setOf(
            "Úložiště"
        )

        "ru".toLang() == lang -> setOf(
            "Хранилище",
            "Память",
            // Texet/TM-5083/TM-5083:8.1.0/O11019/1559125490:user/release-keys
            "Накопители"
        )

        "es".toLang() == lang -> setOf(
            "Almacenamiento",
            // https://github.com/d4rken/sdmaid-public/issues/3861
            // LANIX/Ilium_X520/Ilium_X520:7.0/NRD90M/X520_TELCEL_SW_19:user/release-keys
            "Espacio de almacenamiento"
        )

        "zh-Hans".toLoc().let { it.language == lang && it.script == script } -> setOf(
            // Simplified
            "存储"
        )

        "zh-Hant".toLoc().let { it.language == lang && it.script == script } -> setOf(
            // Traditional
            "儲存空間",
            // LG V30+  H930DS Android Pie non root
            "儲存裝置"
        )

        "zh".toLang() == lang -> setOf("存储")
        "ja".toLang() == lang -> setOf("ストレージ")
        "pt".toLang() == lang -> setOf("Armazenamento")
        "in".toLang() == lang -> setOf("Penyimpanan")
        "hi".toLang() == lang -> setOf(
            "मेमोरी"
        )

        "it".toLang() == lang -> setOf(
            "Spazio di archiviazione",
            // https://github.com/d4rken/sdmaid-public/issues/2545
            "Memoria archiviazione",
            // https://github.com/d4rken/sdmaid-public/issues/2780
            "Memoria"
        )

        "uk".toLang() == lang -> setOf("Пам'ять")
        "fr".toLang() == lang -> setOf(
            "Stockage"
        )

        "tr".toLang() == lang -> setOf("Depolama")
        "kr".toLang() == lang -> setOf("저장용량")
        "pl".toLang() == lang -> setOf("Pamięć wewnętrzna")
        "vi".toLang() == lang -> setOf(
            // samsung/gracerltexx/gracerlte:9/PPR1.180610.011/N935FXXU4CSC5:user/release-keys/Device locales: [vi_VN,en_US]
            // Order is important because `Bộ nhớ` is used for the memory entry just below the storage entry
            "Lưu trữ",
            "Bộ nhớ"
        )

        "el".toLang() == lang -> setOf("Αποθηκευτικός χώρος")
        "nl".toLang() == lang -> setOf(
            "Opslagruimte"
        )

        "hu".toLang() == lang -> setOf("Tárhely")
        "ko".toLang() == lang -> setOf(
            "저장용량",
            "저장공간",
            // https://github.com/d4rken/sdmaid-public/issues/4534
            "저장 공간"
        )

        "sl".toLang() == lang -> setOf("Shranjevanje")
        "th".toLang() == lang -> setOf("ที่เก็บข้อมูล")
        "iw".toLang() == lang -> setOf("אחסון")
        "ml".toLang() == lang -> setOf(
            // ml_IN @AOSP
            "സ്റ്റോറേജ്"
        )

        "fi".toLang() == lang -> setOf("Tallennustila")
        "ar".toLang() == lang -> setOf(
            // ar_EG @ AOSP
            "التخزين"
        )

        "nb".toLang() == lang -> setOf("Lagring")
        "bg".toLang() == lang -> setOf("Хранилище")
        "sk".toLang() == lang -> setOf("Úložisko")
        "ms".toLang() == lang -> {
            // ROM is not completely translated (AOSP API 27)
            getStorageEntryStatic("en", "")
        }

        "lt".toLang() == lang -> setOf("Saugykla")
        "sv".toLang() == lang -> setOf("Lagring")
        "sr".toLang() == lang -> setOf(
            "Меморија",
            // samsung/beyond1ltexx/beyond1:9/PPR1.180610.011/G973FXXU3ASIG:user/release-keys/en_US
            "Memorija"
        )

        "da".toLang() == lang -> setOf("Lagerplads")
        "ca".toLang() == lang -> setOf("Emmagatzematge")
        "fa".toLang() == lang -> setOf("حافظه")
        "et".toLang() == lang -> setOf("Mäluruum")
        "ro".toLang() == lang -> setOf("Stocare")
        "hr".toLang() == lang -> setOf("Pohranjivanje")
        "bn".toLang() == lang -> setOf("স্টোরেজ")
        "lv".toLang() == lang -> setOf("Krātuve")
        else -> getStorageEntryStatic("en", "")
    }

    fun getClearCacheDynamic(): Set<String>? = context
        .get3rdPartyString(SETTINGS_PKG, "clear_cache_btn_text")
        .also { log(TAG) { "getClearCacheButtonLabels(): $it" } }
        ?.let { setOf(it) }

    // https://github.com/aosp-mirror/platform_packages_apps_settings/blob/pie-platform-release/res
    // clear_cache_btn_text
    fun getClearCacheStatic(lang: String, script: String): Set<String> = when {
        "de".toLang() == lang -> setOf(
            "Cache leeren",
            "CACHE LÖSCHEN"
        )

        "en".toLang() == lang -> setOf("Clear cache")
        "cs".toLang() == lang -> setOf("VYMAZAT MEZIPAMĚŤ")
        "ru".toLang() == lang -> setOf(
            "Очистить кеш",
            // samsung/dreamltexx/dreamlte:8.0.0/R16NW/G950FXXU4CRL3:user/release-keys
            "ОЧИСТИТЬ КЭШ"
        )

        "es".toLang() == lang -> setOf(
            "BORRAR CACHÉ",
            // motorola/deen/deen_sprout:9/PPKS29.68-16-21-5/f3183:user/release-keys/es_US
            "BORRAR MEMORIA CACHÉ",
            "ELIMINAR CACHÉ",
            // https://github.com/d4rken/sdmaid-public/issues/3861
            // LANIX/Ilium_X520/Ilium_X520:7.0/NRD90M/X520_TELCEL_SW_19:user/release-keys
            "ELIMINAR MEMORIA CACHÉ"
        )

        "zh-Hans".toLoc().let { it.language == lang && it.script == script } -> setOf(
            // Simplified
            "清除缓存"
        )

        "zh-Hant".toLoc().let { it.language == lang && it.script == script } -> setOf(
            // Traditional
            "清除快取",
            // LG V30+  H930DS Android Pie non root
            "清除快取資料"
        )

        "zh".toLang() == lang -> setOf("清除缓存")
        "ja".toLang() == lang -> setOf("キャッシュを削除")
        "pt".toLang() == lang -> setOf("LIMPAR CACHE")
        "in".toLang() == lang -> setOf("Hapus cache")
        "hi".toLang() == lang -> setOf("कैश साफ़ करें")
        "it".toLang() == lang -> setOf(
            // https://github.com/d4rken/sdmaid-public/issues/2545
            "Svuota cache",
            // https://github.com/d4rken/sdmaid-public/issues/2542
            "CANCELLA CACHE"
        )

        "uk".toLang() == lang -> setOf("Очистити кеш")
        "fr".toLang() == lang -> setOf(
            "Vider le cache",
            // Sony/H8324/H8324:9/52.0.A.8.50/2936700587:user/release-keys/fr_FR
            "EFFACER LE CACHE"
        )

        "tr".toLang() == lang -> setOf("Önbelleği temizle")
        "kr".toLang() == lang -> setOf("캐시 지우기")
        "pl".toLang() == lang -> setOf("Wyczyść pamięć podręczną")
        "vi".toLang() == lang -> setOf(
            "Xóa bộ nhớ đệm",
            // samsung/gracerltexx/gracerlte:9/PPR1.180610.011/N935FXXU4CSC5:user/release-keys/Device locales: [vi_VN,en_US]
            "Xóa bộ đệm"
        )

        "el".toLang() == lang -> setOf("Διαγραφή προσωρινής μνήμης")
        "nl".toLang() == lang -> setOf("Cache wissen")
        "hu".toLang() == lang -> setOf("A gyorsítótár törlése")
        "ko".toLang() == lang -> setOf(
            "캐시 지우기",
            "캐시 삭제",
            // https://github.com/d4rken/sdmaid-public/issues/4534
            "임시 파일 삭제"
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
        else -> getClearCacheStatic("en", "")
    }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "AOSP", "Labels", "14Plus")
        val SETTINGS_PKG = "com.android.settings".toPkgId()
    }
}