package eu.darken.sdmse.appcleaner.core.automation.specs

import android.content.Context
import android.os.Build
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
import eu.darken.sdmse.automation.core.pkgId
import eu.darken.sdmse.common.DeviceDetective
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.isInstalled
import eu.darken.sdmse.common.pkgs.toPkgId
import javax.inject.Inject

@Reusable
open class MIUI11Specs @Inject constructor(
    private val ipcFunnel: IPCFunnel,
    @ApplicationContext private val context: Context,
    private val deviceDetective: DeviceDetective,
    private val pkgRepo: PkgRepo,
) : AutomationStepGenerator {

    override val label = TAG

    override suspend fun isResponsible(pkg: Installed): Boolean {
        if (deviceDetective.isCustomROM()) return false
        if (!hasApiLevel(26) || !deviceDetective.isXiaomi()) return false
        // Xiaomi/raphael_eea/raphael:10/QKQ1.190825.002/V11.0.3.0.QFKEUXM:user/release-keys
        if (VERSION_STARTS.none { Build.VERSION.INCREMENTAL.startsWith(it) }) return false
        return pkgRepo.isInstalled(SETTINGS_PKG)
    }

    fun getDialogTitles(lang: String, script: String, country: String?): Collection<String> {
        context.get3rdPartyString(SETTINGS_PKG, "app_manager_dlg_clear_cache_title")?.let {
            log(TAG) { "Using label from APK: $it" }
            return setOf(it)
        }
        return when {
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
    }

    fun getClearCacheButtonLabels(lang: String, script: String, country: String?): Collection<String> {
        context.get3rdPartyString(SETTINGS_PKG, "app_manager_clear_cache")?.let {
            log(TAG) { "Using label from APK: $it" }
            return setOf(it)
        }
        return when {
            "en".toLang() == lang -> setOf("Clear cache")
            "de".toLang() == lang -> setOf("Cache löschen")
            "cs".toLang() == lang -> setOf("Vyčistit mezipaměť")
            "ru".toLang() == lang -> setOf("Очистить кэш")
            "es".toLang() == lang -> setOf("Limpiar caché")
            "zh-Hant".toLoc().let {
                it.language == lang && (it.script == script || setOf(
                    "HK",
                    "TW"
                ).contains(country))
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
    }

    fun getClearDataButtonLabels(lang: String, script: String, country: String?): Collection<String> {
        context.get3rdPartyString(SETTINGS_PKG, "app_manager_menu_clear_data")?.let {
            log(TAG) { "Using label from APK: $it" }
            return setOf(it)
        }
        return when {
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
    }

    override suspend fun getSpecs(pkg: Installed): List<AutomationCrawler.Step> {
        val locale = getSysLocale()
        val lang = locale.language
        val script = locale.script
        val country = locale.country
        log(TAG, VERBOSE) { "Getting specs for ${pkg.packageName} (lang=$lang, script=$script)" }

        val steps = mutableListOf<AutomationCrawler.Step>()

        val clearDataLabels = getClearDataButtonLabels(lang, script, country)
        val clearCacheLabels = getClearCacheButtonLabels(lang, script, country)
        val dialogTitles = getDialogTitles(lang, script, country)

        run {
            val clearDataFilter: suspend (AccessibilityNodeInfo) -> Boolean = filter@{ node ->
                if (!node.isClickyButton()) return@filter false

                if (node.textMatchesAny(clearDataLabels)) return@filter true

                if (node.textMatchesAny(clearCacheLabels)) {
                    val altStep = AutomationCrawler.Step(
                        parentTag = TAG,
                        pkgInfo = pkg,
                        label = "BRANCH: Find & Click 'Clear cache' (targets=$clearCacheLabels)",
                        windowNodeTest = CrawlerCommon.windowCriteriaAppIdentifier(SETTINGS_PKG, ipcFunnel, pkg),
                        nodeTest = { it.isClickyButton() && it.textMatchesAny(clearCacheLabels) },
                        action = CrawlerCommon.defaultClick()
                    )
                    throw BranchException(
                        "Got 'Clear cache' instead of 'Clear data' skip the action dialog step.",
                        listOf(altStep),
                        invalidSteps = 1
                    )
                }
                return@filter false
            }

            steps.add(
                AutomationCrawler.Step(
                    parentTag = TAG,
                    pkgInfo = pkg,
                    label = "Find & click MIUI 'Clear data' (targets=$clearDataLabels)",
                    windowIntent = CrawlerCommon.defaultWindowIntent(context, pkg),
                    windowEventFilter = CrawlerCommon.defaultWindowFilter(SETTINGS_PKG),
                    windowNodeTest = CrawlerCommon.windowCriteriaAppIdentifier(SETTINGS_PKG, ipcFunnel, pkg),
                    nodeTest = clearDataFilter,
                    nodeRecovery = CrawlerCommon.getDefaultNodeRecovery(pkg),
                    action = CrawlerCommon.defaultClick()
                )
            )
        }

        // Clear data
        // -> Clear data
        // -> Clear cache
        // -> Cancel
        // This may be skipped when MIUI just shows a 'Clear cache' option
        run {
            val windowCriteria = fun(node: AccessibilityNodeInfo): Boolean {
                if (node.pkgId != SETTINGS_PKG) return false
                return node.crawl().map { it.node }.any { it.idContains("id/alertTitle") }
            }
            val entryFilter = fun(node: AccessibilityNodeInfo): Boolean {
                if (!node.isClickable || !node.isTextView()) return false
                return node.textMatchesAny(clearCacheLabels)
            }

            steps.add(
                AutomationCrawler.Step(
                    parentTag = TAG,
                    pkgInfo = pkg,
                    label = "Find & click 'Clear Cache' entry in bottom sheet (targets=$clearCacheLabels)",
                    windowNodeTest = windowCriteria,
                    nodeTest = entryFilter,
                    action = CrawlerCommon.defaultClick()
                )
            )
        }

        // Clear cache?
        // -> Cancel        -> Ok
        run {
            val windowCriteria = fun(node: AccessibilityNodeInfo): Boolean {
                if (node.pkgId != SETTINGS_PKG) return false
                return node.crawl().map { it.node }.any { subNode ->
                    // This is required to relax the match on the dialog texts
                    // Otherwise it could detect the clear cache button as match
                    if (!subNode.idContains("id/alertTitle")) return@any false
                    return@any when {
                        subNode.textMatchesAny(dialogTitles) -> true
                        subNode.textMatchesAny(dialogTitles.map { it.replace("?", "") }) -> true
                        subNode.textMatchesAny(dialogTitles.map { "$it?" }) -> true
                        subNode.textEndsWithAny(clearCacheLabels.map { "$it?" }) -> true
                        subNode.textEndsWithAny(clearCacheLabels) -> true
                        else -> false
                    }
                }

            }

            val buttonFilter = fun(node: AccessibilityNodeInfo): Boolean {
                if (!node.isClickyButton()) return false
                return when (Bugs.isDryRun) {
                    true -> node.idMatches("android:id/button2")
                    false -> node.idMatches("android:id/button1")
                }
            }

            steps.add(
                AutomationCrawler.Step(
                    parentTag = TAG,
                    pkgInfo = pkg,
                    label = "Find & click 'OK' in confirmation dialog",
                    windowNodeTest = windowCriteria,
                    nodeTest = buttonFilter,
                    action = CrawlerCommon.defaultClick()
                )
            )
        }

        return steps
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: MIUI11Specs): AutomationStepGenerator
    }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "MIUI11Specs")
        private val SETTINGS_PKG = "com.miui.securitycenter".toPkgId()
        private val VERSION_STARTS = listOf("V10", "V11")
    }
}