package eu.darken.sdmse.appcleaner.core.automation.specs.aosp

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.automation.core.common.AutomationLabelSource
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.toPkgId
import javax.inject.Inject

class AOSPLabels29Plus @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aospLabels14Plus: AOSPLabels14Plus,
) : AutomationLabelSource {

    fun getStorageEntryDynamic(): Set<String>? = context
        .get3rdPartyString(SETTINGS_PKG, "storage_settings_for_app")
        .also { log(TAG) { "getStorageEntryLabel(): $it" } }
        ?.let { setOf(it) }

    // https://github.com/aosp-mirror/platform_packages_apps_settings/tree/android10-mainline-release/res
    // storage_settings_for_app
    fun getStorageEntryStatic(lang: String, script: String): Set<String> = when {
        "de".toLang() == lang -> setOf(
            "Speicher und Cache",
            // OnePlus/DN2103EEA/OP515BL1:11/RP1A.200720.011/1632390704634:user/release-keys
            "Speichernutzung"
        )

        "en".toLang() == lang -> setOf(
            "Storage & cache",
            // google/sargo/sargo:11/RPB3.200720.005/6705141:user/release-keys
            "Storage and cache",
            // OPPO/CPH2249/OP4F81L1:11/RP1A.200720.011/1632508695807:user/release-keys
            "Storage usage"
        )

        "cs".toLang() == lang -> setOf("Úložiště a mezipaměť")
        "ru".toLang() == lang -> setOf(
            "Хранилище и кэш",
            // google/bonito/bonito:10/QQ1A.191205.011/6009058:user/release-keys ru_RU
            "Хранилище и кеш",
            // CUBOT/NOTE_9_EEA/NOTE_9:11/R01005/1638771465:user/release-keys
            "Память и кэш",
            // OnePlus/OnePlus9_IND/OnePlus9:12/SKQ1.210216.001/R.202201181959:user/release-keys ru_KZ,en_US
            "Использование памяти"
        )

        "es".toLang() == lang -> setOf(
            // OnePLUS A60003_22_1900712 @ Oxigen OS 9.0.5 (Android 10)
            "Almacenamiento y caché"
        )

        "zh-Hans".toLoc().let { it.language == lang && it.script == script } -> setOf(
            // Simplified zh-CN
            "存储和缓存"
        )

        "zh-Hant".toLoc().let { it.language == lang && it.script == script } -> setOf(
            // Traditional zh-HK
            "儲存空間與快取空間",
            // google/taimen/taimen:10/QP1A.191005.007.A1/5908163:user/release-keys/zh_TW
            "儲存空間和快取"
        )

        "zh".toLang() == lang -> setOf("存储和缓存")
        "ja".toLang() == lang -> setOf("ストレージとキャッシュ")
        "pt".toLang() == lang -> setOf("Armazenamento e cache")
        "hi".toLang() == lang -> setOf("स्टोरेज और कैश")
        "it".toLang() == lang -> setOf(
            // OnePlus/OnePlus7Pro/OnePlus7Pro:10/QKQ1.190716.003/1909010630:user/release-keys
            "Spazio di archiviazione e cache"
        )

        "uk".toLang() == lang -> setOf("Пам’ять і кеш")
        "fr".toLang() == lang -> setOf("Espace de stockage et cache")
        "tr".toLang() == lang -> setOf("Depolama alanı ve önbellek")
        "pl".toLang() == lang -> setOf(
            "Pamięć i pamięć podręczna",
            // google/sunfish/sunfish:12/SP1A.211105.002/7743617:user/release-keys
            "Pamięć wewnętrzna i podręczna"
        )

        "vi".toLang() == lang -> setOf("Bộ nhớ và bộ nhớ đệm")
        "el".toLang() == lang -> setOf("Αποθηκευτικός χώρος και κρυφή μνήμη")
        "nl".toLang() == lang -> setOf("Opslag en cache")
        "hu".toLang() == lang -> setOf("Tárhely és gyorsítótár")
        "ko".toLang() == lang -> setOf("저장용량 및 캐시")
        "sl".toLang() == lang -> setOf("Shramba in predpomnilnik")
        "th".toLang() == lang -> setOf("พื้นที่เก็บข้อมูลและแคช")
        "iw".toLang() == lang -> setOf("אחסון ומטמון")
        "ml".toLang() == lang -> setOf("സ്\u200Cറ്റോറേജും കാഷെയും")
        "fi".toLang() == lang -> setOf("Tallennustila ja välimuisti")
        "ar".toLang() == lang -> setOf(
            "التخزين وذاكرة التخزين المؤقت",
            // OnePlus/OnePlus7TPro/OnePlus7TPro:11/RKQ1.201022.002/2105071700:user/release-keys
            "مساحة التخزين وذاكرة التخزين المؤقت"
        )

        "nb".toLang() == lang -> setOf("Lagring og buffer")
        "bg".toLang() == lang -> setOf("Хранилище и кеш")
        "sk".toLang() == lang -> setOf("Úložisko a vyrovnávacia pamäť")
        "ms".toLang() == lang -> setOf("Storan & cache")
        "lt".toLang() == lang -> setOf("Saugykla ir talpykla")
        "sv".toLang() == lang -> setOf("Lagringsutrymme och cacheminne")
        "sr".toLang() == lang -> setOf("Меморијски простор и кеш")
        "da".toLang() == lang -> setOf("Lagerplads og cache")
        "ca".toLang() == lang -> setOf("Emmagatzematge i memòria cau")
        "fa".toLang() == lang -> setOf("فضای ذخیره\u200Cسازی و حافظه پنهان")
        "in".toLang() == lang -> setOf("Penyimpanan & cache")
        "ro".toLang() == lang -> setOf(
            "Spațiul de stocare",
            // OnePlus/OnePlusNordCE_EEA/OnePlusNordCE:11/RKQ1.201217.002/2107220023:user/release-keys
            "Spațiul de stocare și memoria cache"
        )

        "pa".toLang() == lang -> setOf("ਸਟੋਰੇਜ ਅਤੇ ਕੈਸ਼ੇ")
        "az".toLang() == lang -> setOf("Depo")
        else -> getStorageEntryStatic("en", "")
    }.tryAppend { aospLabels14Plus.getStorageEntryStatic(lang, script) }

    fun getClearCacheDynamic(): Set<String>? = aospLabels14Plus.getClearCacheDynamic()

    // https://github.com/aosp-mirror/platform_packages_apps_settings/blob/pie-platform-release/res
    // clear_cache_btn_text
    fun getClearCacheStatic(lang: String, script: String): Set<String> =
        aospLabels14Plus.getClearCacheStatic(lang, script)

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "AOSP", "Labels", "29Plus")
        val SETTINGS_PKG = "com.android.settings".toPkgId()
    }
}
