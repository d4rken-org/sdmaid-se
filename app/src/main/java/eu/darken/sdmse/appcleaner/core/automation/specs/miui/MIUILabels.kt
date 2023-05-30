package eu.darken.sdmse.appcleaner.core.automation.specs.miui

import android.content.Context
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.automation.core.common.AutomationLabelSource
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.toPkgId
import javax.inject.Inject

@Reusable
class MIUILabels @Inject constructor(
    @ApplicationContext private val context: Context,
) : AutomationLabelSource {

    fun getDialogTitles(lang: String, script: String, country: String?): Collection<String> =
        getDialogTitlesDynamic() ?: getDialogTitlesFallback(lang, script, country)

    private fun getDialogTitlesDynamic() = context
        .get3rdPartyString(SETTINGS_PKG, "app_manager_dlg_clear_cache_title")
        .also { log(TAG) { "getDialogTitle(): $it" } }
        ?.let { setOf(it) }

    private fun getDialogTitlesFallback(lang: String, script: String, country: String?): Collection<String> = when {
        "en".toLang() == lang -> setOf("Clear cache?")
        "de".toLang() == lang -> setOf("Cache löschen?")
        "cs".toLang() == lang -> setOf("Vyčistit mezipaměť?")
        "ru".toLang() == lang -> setOf("Очистить кэш?")
        "es".toLang() == lang -> setOf("¿Borrar caché?")
        "zh-Hant".toLoc().let {
            it.language == lang && (it.script == script || setOf(
                "HK",
                "TW"
            ).contains(country))
        } -> setOf("確定清除應用暫存？")

        "zh".toLang() == lang -> setOf("确定清除应用缓存？")
        "ja".toLang() == lang -> setOf("キャッシュをクリアしますか？")
        "pt".toLang() == lang -> setOf("Limpar cache?")
        "id".toLang() == lang -> setOf("Hapus cache?")
        "hi".toLang() == lang -> setOf("कैशे मिटाएं?")
        "it".toLang() == lang -> setOf("Svuotare la cache?")
        "uk".toLang() == lang -> setOf("Очистити кеш?")
        "fr".toLang() == lang -> setOf("Vider le cache?")
        "tr".toLang() == lang -> setOf("Önbellek temizlensin mi?")
        "pl".toLang() == lang -> setOf(
            "Wyczyścić pamięć podręczną?",
            // Xiaomi/venus/venus:11/RKQ1.200928.002/V12.5.15.0.RKBCNXM:user/release-keys
            "Usunąć pamięć podręczną?"
        )

        "nl".toLang() == lang -> setOf("Cache wissen?")
        "hu".toLang() == lang -> setOf("Törli a gyorsítótárat?")
        "ko".toLang() == lang -> setOf("캐시를 지우시겠습니까?")
        "sl".toLang() == lang -> setOf(
            "Počistim predpomnilnik?",
            "Počisti predpomnilnik?",
            "Želite počistiti predpomnilnik?"
        )

        "az".toLang() == lang -> setOf("Keş təmizlənsin?")
        "ms".toLang() == lang -> setOf("Bersihkan cache?", "Kosongkan cache")
        "bs".toLang() == lang -> setOf("Želite li izbrisati predmemoriju?")
        "ca".toLang() == lang -> setOf("Voleu esborra la memòria cau?")
        "da".toLang() == lang -> setOf("Ryd cache?")
        "et".toLang() == lang -> setOf("Kustuta vahemälu?")
        "eu".toLang() == lang -> setOf("Cache garbitu?")
        "gl".toLang() == lang -> setOf("Eliminar a caché?")
        "ha".toLang() == lang -> setOf("A share gurbin bayanai?")
        "hr".toLang() == lang -> setOf("Izbrisati predmemoriju?", "Očistiti predmemoriju?")
        "lv".toLang() == lang -> setOf("Tīrīt kešatmiņu?")
        "lt".toLang() == lang -> setOf("Valyti podėlį?")
        "mt".toLang() == lang -> setOf("Trid tbattal il-cache?")
        "nb".toLang() == lang -> setOf("Tømme cache?")
        "uz".toLang() == lang -> setOf("Keshni tozalash?")
        "ro".toLang() == lang -> setOf("Şterge cache?", "Șterge cache?", "Ştergeți cache?")
        "sq".toLang() == lang -> setOf("Pastro deponë?")
        "sk".toLang() == lang -> setOf("Vymazať cache?", "Vymazať vyrovnávaciu pamäť?")
        "fi".toLang() == lang -> setOf("Tyhjennä välimuisti?")
        "sv".toLang() == lang -> setOf("Rensa cache?")
        "vi".toLang() == lang -> setOf("Xóa bộ nhớ đệm?")
        "el".toLang() == lang -> setOf("Εκκαθάριση προσωρινή μνήμης;")
        "be".toLang() == lang -> setOf("Ачысціць кэш?")
        "bg".toLang() == lang -> setOf("Изчисти кеша?")
        "kk".toLang() == lang -> setOf("Кэш тазалансын ба?")
        "mk".toLang() == lang -> setOf("Да се избрише кеш меморијата?")
        "sr".toLang() == lang -> setOf("Очисти кеш?", "Очистити кеш?")
        "ka".toLang() == lang -> setOf("გავწმინდო ქეში?")
        "hy".toLang() == lang -> setOf("Մաքրե՞լ քեշը:")
        "iw".toLang() == lang -> setOf("לנקות מטמון?")
        "ur".toLang() == lang -> setOf("کیشے صاف کریں؟")
        "ar".toLang() == lang -> setOf("مسح الذاكرة المؤقتة؟")
        "fa".toLang() == lang -> setOf("حافظه پنهان پاک شود؟")
        "ne".toLang() == lang -> setOf("क्यास खाली गर्नुहुन्छ?")
        "mr".toLang() == lang -> setOf("कॅचे पुसायची?")
        "as".toLang() == lang -> setOf("কেশ্ব পৰিষ্কাৰ কৰিবনে?")
        "bn".toLang() == lang -> setOf("ক্যাশে পরিষ্কার করবেন?")
        "pa".toLang() == lang -> setOf("ਕੈਸ਼ੇ ਹਟਾਉਣੇ ਹਨ?")
        "gu".toLang() == lang -> setOf("કૅશ સાફ કરીએ?")
        "ta".toLang() == lang -> setOf("தேக்ககத்தை அழிக்கவா?")
        "te".toLang() == lang -> setOf("కాష్\u200Cను తీసివేయాలా?")
        "kn".toLang() == lang -> setOf("ಕ್ಯಾಶೆ ತೆರವುಗೊಳಿಸುವುದೇ?")
        "ml".toLang() == lang -> setOf("കാഷേ മായ്\u200Cക്കണോ?")
        "th".toLang() == lang -> setOf("ล้างหน่วยความจำแคช?", "ล้างแคช?")
        "my".toLang() == lang -> setOf("ကက်ချ်အား ရှင်းပစ်မလား?")
        "km".toLang() == lang -> setOf("ជម្រះឃ្លាំងសម្ងាត់ឬ?")
        "or".toLang() == lang -> setOf("କ୍ୟାଚେ ସଫା କରିବେ?")
        "lo".toLang() == lang -> setOf("ລົບ\u200Bລ້າງ Cache?")
        else -> throw UnsupportedOperationException()
    }

    fun getClearCacheButtonLabels(lang: String, script: String, country: String?): Collection<String> =
        getClearCacheButtonLabelDynamic() ?: getClearCacheButtonLabelsFallback(lang, script, country)

    private fun getClearCacheButtonLabelDynamic() = context
        .get3rdPartyString(SETTINGS_PKG, "app_manager_clear_cache")
        .also { log(TAG) { "getClearCacheButtonLabel(): $it" } }
        ?.let { setOf(it) }

    private fun getClearCacheButtonLabelsFallback(lang: String, script: String, country: String?): Collection<String> =
        when {
            "en".toLang() == lang -> setOf("Clear cache")
            "de".toLang() == lang -> setOf("Cache löschen")
            "cs".toLang() == lang -> setOf("Vyčistit mezipaměť")
            "ru".toLang() == lang -> setOf("Очистить кэш")
            "es".toLang() == lang -> setOf("Limpiar caché")
            "zh-Hant".toLoc().let {
                it.language == lang && (it.script == script || setOf("HK", "TW").contains(country))
            } -> setOf("清除暫存")

            "zh".toLang() == lang -> setOf("清除缓存")
            "ja".toLang() == lang -> setOf("キャッシュをクリア")
            "pt".toLang() == lang -> setOf("Limpar cache")
            "id".toLang() == lang -> setOf("Bersihkan cache")
            "hi".toLang() == lang -> setOf("कैशे मिटाएं")
            "it".toLang() == lang -> setOf("Svuota la cache")
            "uk".toLang() == lang -> setOf("Очистити кеш")
            "fr".toLang() == lang -> setOf("Vider le cache")
            "tr".toLang() == lang -> setOf("Önbelleği temizle")
            "pl".toLang() == lang -> setOf(
                "Wyczyść pamięć podręczną",
                // Xiaomi/venus/venus:11/RKQ1.200928.002/V12.5.16.0.RKBCNXM:user/release-keys
                "Czyść pamięć podręczną"
            )

            "nl".toLang() == lang -> setOf("Cache wissen")
            "hu".toLang() == lang -> setOf("Gyorsítótártörlés")
            "ko".toLang() == lang -> setOf("캐시 지우기")
            "sl".toLang() == lang -> setOf("Očisti predpomnilnik", "Počisti predpomnilnik")
            "az".toLang() == lang -> setOf("Keşi təmizlə")
            "ms".toLang() == lang -> setOf("Bersihkan cache", "Kosongkan cache")
            "bs".toLang() == lang -> setOf("Izbriši predmemoriju")
            "ca".toLang() == lang -> setOf("Esborra la memòria cau")
            "da".toLang() == lang -> setOf("Ryd cache")
            "et".toLang() == lang -> setOf("Puhasta vahemälu")
            "eu".toLang() == lang -> setOf("Garbitu cache-a")
            "gl".toLang() == lang -> setOf("Eliminar a caché")
            "ha".toLang() == lang -> setOf("Share gurbin bayanai")
            "hr".toLang() == lang -> setOf("Očisti predmemoriju")
            "lv".toLang() == lang -> setOf("Tīrīt kešatmiņu")
            "lt".toLang() == lang -> setOf("Valyti podėlį")
            "mt".toLang() == lang -> setOf("Battal il-cache")
            "nb".toLang() == lang -> setOf("Tøm cache")
            "uz".toLang() == lang -> setOf("Keshni tozalash")
            "ro".toLang() == lang -> setOf("Şterge cache", "Șterge cache")
            "sq".toLang() == lang -> setOf("Pastro deponë")
            "sk".toLang() == lang -> setOf("Vyčistiť cache", "Vymazať vyrovnávaciu pamäť")
            "fi".toLang() == lang -> setOf("Tyhjennä välimuisti")
            "sv".toLang() == lang -> setOf("Rensa cache")
            "vi".toLang() == lang -> setOf("Xóa bộ nhớ đệm")
            "el".toLang() == lang -> setOf("Εκκαθάριση προσωρινή μνήμης")
            "be".toLang() == lang -> setOf("Ачысціць кэш")
            "bg".toLang() == lang -> setOf("Изчисти кеша")
            "kk".toLang() == lang -> setOf("Кэшті тазалау")
            "mk".toLang() == lang -> setOf("Исчисти кеш меморија")
            "sr".toLang() == lang -> setOf("Очисти кеш")
            "ka".toLang() == lang -> setOf("ქეშის გასუფთავება")
            "hy".toLang() == lang -> setOf("Մաքրել քեշը")
            "iw".toLang() == lang -> setOf("ניקוי מטמון")
            "ur".toLang() == lang -> setOf("کیشے صاف کریں")
            "ar".toLang() == lang -> setOf("مسح الذاكرة المؤقتة")
            "fa".toLang() == lang -> setOf("پاک\u200Cسازی حافظه پنهان")
            "ne".toLang() == lang -> setOf("क्यास खाली गर्नुहोस्")
            "mr".toLang() == lang -> setOf("कॅचे पुसा")
            "as".toLang() == lang -> setOf("কেশ্ব পৰিষ্কাৰ কৰক")
            "bn".toLang() == lang -> setOf("ক্যাশে পরিষ্কার করুন")
            "pa".toLang() == lang -> setOf("ਕੈਸ਼ੇ ਸਾਫ਼ ਕਰੋ")
            "gu".toLang() == lang -> setOf("કૅશ સાફ કરો")
            "ta".toLang() == lang -> setOf("தேக்ககத்தை அழி")
            "te".toLang() == lang -> setOf("కాష్\u200Cని తొలగించు")
            "kn".toLang() == lang -> setOf("ಕ್ಯಾಶೆ ಅಳಿಸಿ")
            "ml".toLang() == lang -> setOf("കാഷേ മായ്\u200Cക്കുക")
            "th".toLang() == lang -> setOf("ล้างหน่วยความจำแคช", "ล้างแคช")
            "my".toLang() == lang -> setOf("ကက်ချ်ကို ရှင်းလင်းမည်")
            "km".toLang() == lang -> setOf("ជម្រះឃ្លាំងសម្ងាត់")
            "or".toLang() == lang -> setOf("କ୍ୟାଚେ ସଫା କରନ୍ତୁ")
            "lo".toLang() == lang -> setOf("ລົບ\u200Bລ້າງ Cache")
            else -> throw UnsupportedOperationException()
        }

    fun getClearDataButtonLabels(lang: String, script: String, country: String?): Collection<String> =
        getClearDataButtonLabelsDynamic() ?: getClearDataButtonLabelsFallback(lang, script, country)

    private fun getClearDataButtonLabelsDynamic() = context
        .get3rdPartyString(SETTINGS_PKG, "app_manager_menu_clear_data")
        .also { log(TAG) { "getClearDataButtonLabel(): $it" } }
        ?.let { setOf(it) }

    private fun getClearDataButtonLabelsFallback(lang: String, script: String, country: String?): Collection<String> =
        when {
            "en".toLang() == lang -> setOf("Clear data")
            "de".toLang() == lang -> setOf("Daten löschen")
            "cs".toLang() == lang -> setOf("Vymazat data")
            "ru".toLang() == lang -> setOf("Очистить")
            "es".toLang() == lang -> setOf("Limpiar datos")
            "zh-Hant".toLoc().let {
                it.language == lang && (it.script == script || setOf(
                    "HK",
                    "TW"
                ).contains(country))
            } -> setOf("清除資料")

            "zh".toLang() == lang -> setOf("清除数据")
            "ja".toLang() == lang -> setOf("データをクリア")
            "pt".toLang() == lang -> setOf("Limpar dados")
            "id".toLang() == lang -> setOf("Hapus data")
            "hi".toLang() == lang -> setOf("डेटा मिटाएं")
            "it".toLang() == lang -> setOf("Elimina dati")
            "uk".toLang() == lang -> setOf("Очистити дані")
            "fr".toLang() == lang -> setOf("Effacer les données")
            "tr".toLang() == lang -> setOf("Verileri temizle")
            "pl".toLang() == lang -> setOf("Wyczyść dane")
            "nl".toLang() == lang -> setOf("Gegevens wissen")
            "hu".toLang() == lang -> setOf("Adattörlés")
            "ko".toLang() == lang -> setOf("데이터 지우기")
            "sl".toLang() == lang -> setOf("Počisti podatke")
            "az".toLang() == lang -> setOf("Məlumatları təmizlə")
            "ms".toLang() == lang -> setOf("Kosongkan data")
            "bs".toLang() == lang -> setOf("Izbriši podatke")
            "ca".toLang() == lang -> setOf("Esborra les dades")
            "da".toLang() == lang -> setOf("Ryd data")
            "et".toLang() == lang -> setOf("Puhasta andmed")
            "eu".toLang() == lang -> setOf("Datuak ezabatu")
            "gl".toLang() == lang -> setOf("Eliminar datos")
            "ha".toLang() == lang -> setOf("Share bayanai")
            "hr".toLang() == lang -> setOf("Izbriši podatke")
            "lv".toLang() == lang -> setOf("Notīrīt datus")
            "lt".toLang() == lang -> setOf("Išvalyti duomenis")
            "mt".toLang() == lang -> setOf("Neħħi d-dejta")
            "nb".toLang() == lang -> setOf("Slett data")
            "uz".toLang() == lang -> setOf("Ma’lumotlarni tozalash")
            "ro".toLang() == lang -> setOf("Şterge date", "Șterge date")
            "sq".toLang() == lang -> setOf("Pastro të dhënat")
            "sk".toLang() == lang -> setOf("Vymazať dáta")
            "fi".toLang() == lang -> setOf("Tyhjennä tiedot")
            "sv".toLang() == lang -> setOf("Rensa data")
            "vi".toLang() == lang -> setOf("Xóa dữ liệu")
            "el".toLang() == lang -> setOf("Εκκαθάριση δεδομένων")
            "be".toLang() == lang -> setOf("Ачысціць дадзеныя")
            "bg".toLang() == lang -> setOf("Изчисти данни")
            "kk".toLang() == lang -> setOf("Деректерді жою")
            "mk".toLang() == lang -> setOf("Избриши податоци")
            "sr".toLang() == lang -> setOf("Избриши податке")
            "ka".toLang() == lang -> setOf("მონაცემების გასუფთავება")
            "hy".toLang() == lang -> setOf("Մաքրել տվյալները")
            "iw".toLang() == lang -> setOf("נקה נתונים")
            "ur".toLang() == lang -> setOf("ڈیٹا صاف کریں")
            "ar".toLang() == lang -> setOf("مسح البيانات")
            "fa".toLang() == lang -> setOf("پاک کردن داده\u200Cها")
            "ne".toLang() == lang -> setOf("डाटा खाली गर्नुहोस्")
            "mr".toLang() == lang -> setOf("डेटा साफ करा")
            "as".toLang() == lang -> setOf("ডাটা পৰিষ্কাৰ কৰক")
            "bn".toLang() == lang -> setOf("ডেটা পরিষ্কার করুন")
            "pa".toLang() == lang -> setOf("ਡਾਟਾ ਸਾਫ਼ ਕਰੋ")
            "gu".toLang() == lang -> setOf("ડેટા સાફ કરો")
            "ta".toLang() == lang -> setOf("தரவை அழி")
            "te".toLang() == lang -> setOf("డేటా తొలగించండి")
            "kn".toLang() == lang -> setOf("ಡೇಟಾ ಅಳಿಸಿ")
            "ml".toLang() == lang -> setOf("ഡാറ്റ മായ്\u200Cക്കുക")
            "th".toLang() == lang -> setOf("ล้างข้อมูล")
            "my".toLang() == lang -> setOf("ဒေတာရှင်းပါ")
            "km".toLang() == lang -> setOf("ជម្រះទិន្នន័យ")
            "or".toLang() == lang -> setOf("ଡାଟା ଖାଲିକରନ୍ତୁ")
            "lo".toLang() == lang -> setOf("ລົບ\u200Bລ້າງ\u200Bຂໍ້\u200Bມູນ")
            else -> throw UnsupportedOperationException()
        }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "MIUI", "Labels")
        private val SETTINGS_PKG = "com.miui.securitycenter".toPkgId()
    }
}