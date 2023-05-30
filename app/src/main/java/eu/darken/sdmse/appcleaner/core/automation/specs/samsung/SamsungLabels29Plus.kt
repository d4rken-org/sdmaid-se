package eu.darken.sdmse.appcleaner.core.automation.specs.samsung

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.automation.core.common.AutomationLabelSource
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.toPkgId
import javax.inject.Inject

class SamsungLabels29Plus @Inject constructor(
    @ApplicationContext private val context: Context,
    private val samsungLabels14Plus: SamsungLabels14Plus,
) : AutomationLabelSource {

    fun getStorageEntryLabel(): String? = context.get3rdPartyString(
        SETTINGS_PKG,
        "storage_settings"
    ).also { log(TAG) { "getStorageEntryLabel(): $it" } }

    fun getStorageEntryLabels(lang: String, script: String): Collection<String> = when {
        // https://github.com/d4rken/sdmaid-public/issues/4124
        // samsung/a41eea/a41:10/QP1A.190711.020/A415FXXU1ATH2:user/release-keys
        "pl".toLang() == lang -> setOf("Domyślna pamięć")
        "th".toLang() == lang -> setOf("ที่เก็บ")
        // Galaxy A21s (SM-A217F) 10/QP1A.190711.020.A217FXXU3ATJ3
        "is".toLang() == lang -> setOf("Geymsla")
        //Galaxy S8 (SM-G950F) 9/PPR1.180610.011.G950FXXSBDTJ1
        "ka".toLang() == lang -> setOf("მეხსიერება")
        // Galaxy Note9 (SM-N960F) 10/QP1A.190711.020.N960FXXU6FTK1
        "bs".toLang() == lang -> setOf("Pohrana")
        // Galaxy A11 (SM-A115F) API 10
        "az".toLang() == lang -> setOf("Ehtiyat")
        // Galaxy A30s (SM-A307GN) API 29
        "km".toLang() == lang -> setOf("ឃ្លាំង\u200Bផ្ទុក")
        "en".toLang() == lang -> setOf("Storage")
        "es".toLang() == lang -> setOf("Almacenamiento")
        "eu".toLang() == lang -> setOf("Biltegiratzea")
        "fil".toLang() == lang -> setOf("Storage")
        "fr".toLang() == lang -> setOf("Stockage")
        "ga".toLang() == lang -> setOf("Stóras")
        "gl".toLang() == lang -> setOf("Almacenamento")
        "hr".toLang() == lang -> setOf("Pohrana")
        "in".toLang() == lang -> setOf("Penyimpanan")
        "is".toLang() == lang -> setOf("Geymsla")
        "it".toLang() == lang -> setOf("Memoria archiviazione")
        "lv".toLang() == lang -> setOf("Krātuve")
        "lt".toLang() == lang -> setOf("Saugykla")
        "hu".toLang() == lang -> setOf("Tárhely")
        "ms".toLang() == lang -> setOf("Penyimpanan")
        "nl".toLang() == lang -> setOf("Opslag")
        "nb".toLang() == lang -> setOf("Lagring")
        "uz".toLang() == lang -> setOf("Xotira")
        "pl".toLang() == lang -> setOf("Domyślna pamięć")
        "pt".toLang() == lang -> setOf("Armazenamento")
        "ro".toLang() == lang -> setOf("Stocare")
        "sq".toLang() == lang -> setOf("Arkivimi")
        "de".toLang() == lang -> setOf(
            // samsung/star2ltexx/star2lte:10/QP1A.190711.020/G965FXXUCFTK1:user/release-keys
            "Speicher und Cache"
        )

        "el".toLang() == lang -> setOf(
            // samsung/gta4lwifieea/gta4lwifi:11/RP1A.200720.012/T500XXU3BVA4:user/release-keys
            "Αποθήκευση"
        )

        "uk".toLang() == lang -> setOf(
            // samsung/beyond0qltezh/beyond0q:12/SP1A.210812.016/G9700ZHU6GVB1:user/release-keys
            "Місце збереження"
        )

        else -> emptyList()
    }.tryAppend { samsungLabels14Plus.getStorageEntryLabels(lang, script) }

    fun getClearCacheLabel(): String? = context.get3rdPartyString(
        SETTINGS_PKG,
        "clear_cache_btn_text"
    ).also { log(TAG) { "getClearCacheButtonLabels(): $it" } }

    fun getClearCacheLabels(lang: String, script: String): Collection<String> = when {
        // https://github.com/d4rken/sdmaid-public/issues/4181
        // samsung/a41eea/a41:10/QP1A.190711.020/A415FXXU1ATH2:user/release-keys
        "pl".toLang() == lang -> setOf("Pamięć cache")
        "th".toLang() == lang -> setOf("ลบแคช", "ลบ\u200Bแค\u200Bช")
        // samsung/a6pltedx/a6plte:10/QP1A.190711.020/A605GDXU8CTI2:user/release-keys
        "in".toLang() == lang -> setOf("Hapus memori")
        // Galaxy Note9 (SM-N960F) 10/QP1A.190711.020.N960FXXU6FTK1
        "bs".toLang() == lang -> setOf("Izbriši keš memoriju")
        //Galaxy Note10+ (SM-N975F) / "androidApiLevel": "30",
        "fil".toLang() == lang -> setOf("I-clear ang cache.", "I-clear ang cache")
        // samsung/a20eeea/a20e:10/QP1A.190711.020/A202FXXU3BUB1:user/release-keys
        "cs".toLang() == lang -> setOf("Vymazat paměť")
        // samsung/a40xx/a40:11/RP1A.200720.012/A405FNXXU3CUD3:user/release-keys
        "sk".toLang() == lang -> setOf("Vymazať vyrov. pamäť")
        "en".toLang() == lang -> setOf("Clear cache")
        "es".toLang() == lang -> setOf("Eliminar caché")
        "eu".toLang() == lang -> setOf("Garbitu katxea")
        "fr".toLang() == lang -> setOf("Vider le cache")
        "ga".toLang() == lang -> setOf("Glan taisce")
        "gl".toLang() == lang -> setOf("Borrar caché")
        "hr".toLang() == lang -> setOf("Obriši privrem. mem.")
        "in".toLang() == lang -> setOf("Hapus memori")
        "is".toLang() == lang -> setOf("Hreinsa skyndiminni")
        "it".toLang() == lang -> setOf("Svuota cache")
        "lv".toLang() == lang -> setOf("Notīrīt kešatmiņu")
        "lt".toLang() == lang -> setOf("Valyti talpyklą")
        "hu".toLang() == lang -> setOf("Gyorsítótár törlése")
        "ms".toLang() == lang -> setOf("Padam cache")
        "nl".toLang() == lang -> setOf("Cache legen")
        "nb".toLang() == lang -> setOf("Tøm buffer")
        "uz".toLang() == lang -> setOf("Keshni tozalash")
        "pl".toLang() == lang -> setOf("Wyczyść pamięć")
        "pt".toLang() == lang -> setOf("Limpar cache")
        "ro".toLang() == lang -> setOf("Golire cache")
        "sq".toLang() == lang -> setOf("Pastro memorien spec.")
        // samsung/c2sxeea/c2s:11/RP1A.200720.012/N986BXXS3DUIF:user/release-keys
        // samsung/x1sxeea/x1s:11/RP1A.200720.012/G981BXXSCDUJ5:user/release-keys
        "sv".toLang() == lang -> setOf("Töm cache")
        else -> emptyList()
    }.tryAppend { samsungLabels14Plus.getClearCacheLabels(lang, script) }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "Samsung", "Labels", "29Plus")
        val SETTINGS_PKG = "com.android.settings".toPkgId()
    }
}
