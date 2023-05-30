package eu.darken.sdmse.appcleaner.core.automation.specs.huawei

import android.content.Context
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.appcleaner.core.automation.specs.aosp.AOSPLabels
import eu.darken.sdmse.automation.core.common.AutomationLabelSource
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.toPkgId
import javax.inject.Inject

@Suppress("IntroduceWhenSubject")
@Reusable
class HuaweiLabels @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aospLabels: AOSPLabels,
) : AutomationLabelSource {


    fun getStorageEntryLabel(): String? = context.get3rdPartyString(
        SETTINGS_PKG,
        "storage_settings"
    ).also { log(TAG) { "getStorageEntryLabel(): $it" } }

    fun getStorageEntryLabels(lang: String, script: String) = when {
        "en".toLang() == lang -> setOf(
            // https://github.com/d4rken/sdmaid-public/issues/3576
            // HONOR/PCT-L29RU/HWPCT:10/HUAWEIPCT-L29/10.0.0.195C10:user/release-keys
            "Storage"
        ).tryPrepend { aospLabels.getStorageEntryStatic(lang, script) }

        "ru".toLang() == lang -> setOf(
            // https://github.com/d4rken/sdmaid-public/issues/3576
            // HONOR/PCT-L29RU/HWPCT:10/HUAWEIPCT-L29/10.0.0.195C10:user/release-keys
            "Память"
        ).tryPrepend { aospLabels.getStorageEntryStatic(lang, script) }

        "de".toLang() == lang -> setOf(
            // HUAWEI/VOG-L29EEA/HWVOG:10/HUAWEIVOG-L29/10.0.0.168C431:user/release-keys
            "Speicher"
        )

        "pl".toLang() == lang -> setOf(
            // HUAWEI/VOG-L29EEA/HWVOG:10/HUAWEIVOG-L29/10.0.0.178C431:user/release-keys
            "Pamięć"
        )

        "it".toLang() == lang -> setOf(
            "Spazio di archiviazione e cache",
            // HONOR/JSN-L21/HWJSN-H:10/HONORJSN-L21/10.0.0.175C432:user/release-keys
            "Memoria"
        )

        "ca".toLang() == lang -> setOf(
            // EMUI 11 (Android 10) [Huawei Mate 20 Pro]
            "Emmagatzematge"
        )

        else -> emptySet()
    }.tryAppend { aospLabels.getStorageEntryStatic(lang, script) }

    fun getClearCacheLabel(): String? = context.get3rdPartyString(
        SETTINGS_PKG,
        "clear_cache_btn_text"
    ).also { log(TAG) { "getClearCacheButtonLabels(): $it" } }

    fun getClearCacheLabels(lang: String, script: String): Collection<String> = when {
        "ca".toLang() == lang -> setOf(
            // EMUI 11 (Android 10) [Huawei Mate 20 Pro]
            "Esborrar la memòria cau"
        )

        else -> emptySet()
    }.tryAppend { aospLabels.getClearCacheStatic(lang, script) }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "Huawei", "Labels")
        val SETTINGS_PKG = "com.android.settings".toPkgId()
    }
}