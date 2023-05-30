package eu.darken.sdmse.appcleaner.core.automation.specs.realme

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
class RealmeLabels @Inject constructor(
    @ApplicationContext private val context: Context,
    private val labels14Plus: AOSPLabels14Plus,
) : AutomationLabelSource {

    fun getStorageEntryLabel(): String? = context.get3rdPartyString(
        SETTINGS_PKG,
        "storage_use"
    ).also { log(TAG) { "getStorageEntryLabel(): $it" } }

    fun getStorageEntryLabels(lang: String, script: String) = when {
        "en".toLang() == lang -> setOf("Storage usage")
        "de".toLang() == lang -> setOf("Speichernutzung")
        "fil".toLang() == lang -> setOf("Paggamit ng storage")
        "ru".toLang() == lang -> setOf("Использование памяти")
        "es".toLang() == lang -> setOf(
            // realme/RMX1993EEA/RMX1993L1:11/RKQ1.201112.002/1621946429926:user/release-keys
            "Uso de almacenamiento",
            // realme/RMX2155EEA/RMX2155L1:11/RP1A.200720.011/1634016814456:user/release-keys
            "Uso del almacenamiento"
        )

        "ru".toLang() == lang -> setOf(
            // realme/RMX3301EEA/RED8ACL1:12/SKQ1.211019.001/S.202203141808:user/release-keys
            "Utilizzo memoria"
        )

        else -> emptySet()
    }.tryAppend { labels14Plus.getStorageEntryStatic(lang, script) }

    fun getClearCacheLabel(): String? = context.get3rdPartyString(
        SETTINGS_PKG,
        "clear_cache_btn_text"
    ).also { log(TAG) { "getClearCacheLabel(): $it" } }

    fun getClearCacheLabels(lang: String, script: String): Collection<String> = when {
        "es".toLang() == lang -> setOf(
            // realme/RMX2155EEA/RMX2155L1:11/RP1A.200720.011/1634016814456:user/release-keys
            "Borrar caché"
        )

        "it".toLang() == lang -> setOf("Cancella cache")
        else -> emptySet()
    }.tryAppend { labels14Plus.getClearCacheStatic(lang, script) }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "Realme", "Labels")
        val SETTINGS_PKG = "com.android.settings".toPkgId()
    }
}