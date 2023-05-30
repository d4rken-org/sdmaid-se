package eu.darken.sdmse.appcleaner.core.automation.specs.samsung

import android.content.Context
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.appcleaner.core.automation.specs.aosp.AOSPLabels14Plus
import eu.darken.sdmse.automation.core.common.AutomationLabelSource
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.toPkgId
import javax.inject.Inject

@Reusable
class SamsungLabels14Plus @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aospLabels14Plus: AOSPLabels14Plus,
) : AutomationLabelSource {

    fun getStorageEntryLabel(): String? = context.get3rdPartyString(
        SETTINGS_PKG,
        "storage_settings"
    ).also { log(TAG) { "getStorageEntryLabel(): $it" } }

    fun getStorageEntryLabels(lang: String, script: String): Collection<String> = when {
        "zh-Hant".toLoc().let { it.language == lang && it.script == script } -> setOf(
            // Traditional
            "儲存位置"
        )

        "nl".toLang() == lang -> setOf(
            // samsung/beyond1ltexx/beyond1:9/PPR1.180610.011/G973FXXU3ASJD:user/release-keys
            "Opslag"
        )

        "ms".toLang() == lang -> setOf("Penyimpanan")
        "pl".toLang() == lang -> setOf("Domyślna Pamięć")
        "ar".toLang() == lang -> setOf("مكان التخزين")
        // samsung/a3y17ltexc/a3y17lte:8.0.0/R16NW/A320FLXXU3CRH2:user/release-keys
        "bg".toLang() == lang -> setOf("Устройство за съхранение на данни")
        else -> emptySet()
    }.tryAppend { aospLabels14Plus.getStorageEntryStatic(lang, script) }

    fun getClearCacheLabel(): String? = context.get3rdPartyString(
        SETTINGS_PKG,
        "clear_cache_btn_text"
    ).also { log(TAG) { "getClearCacheButtonLabels(): $it" } }

    fun getClearCacheLabels(lang: String, script: String): Collection<String> = when {
        "zh-Hant".toLoc().let { it.language == lang && it.script == script } -> setOf(
            "清除緩存",
            // samsung/dream2ltexx/dream2lte:9/PPR1.180610.011/G955FXXU5DSHC:user/release-keys
            "清除快取"
        )

        "nl".toLang() == lang -> setOf(
            // samsung/beyond1ltexx/beyond1:9/PPR1.180610.011/G973FXXU3ASJD:user/release-keys
            "Cache legen"
        )

        "ja".toLang() == lang -> setOf("キャッシュを\u200B消去")
        "ms".toLang() == lang -> setOf("Padam cache")
        "pl".toLang() == lang -> setOf("Wyczyść Pamięć")
        "in".toLang() == lang -> setOf(
            // https://github.com/d4rken/sdmaid-public/issues/4785
            // samsung/j4primeltedx/j4primelte:9/PPR1.180610.011/J415FXXS6BTK2:user/release-keys
            "Hapus memori"
        )

        "ar".toLang() == lang -> setOf("مسح التخزين المؤقت", "مسح التخزين المؤقت\u202C")
        "cs".toLang() == lang -> setOf(
            // samsung/j5y17ltexx/j5y17lte:9/PPR1.180610.011/J530FXXS8CUE4:user/release-keys
            "Vymazat paměť"
        )

        "ro".toLang() == lang -> setOf(
            // samsung/dreamltexx/dreamlte:9/PPR1.180610.011/G950FXXUCDUD1:user/release-keys
            "Golire cache"
        )

        else -> emptySet()
    }.tryAppend { aospLabels14Plus.getClearCacheStatic(lang, script) }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "Samsung", "Labels", "14Plus")
        val SETTINGS_PKG = "com.android.settings".toPkgId()
    }
}