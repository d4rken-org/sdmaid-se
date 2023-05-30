package eu.darken.sdmse.appcleaner.core.automation.specs.lge

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
class LGELabels @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aospLabels: AOSPLabels,
) : AutomationLabelSource {

    fun getStorageEntryLabel(): String? = context.get3rdPartyString(
        SETTINGS_PKG,
        "storage_settings"
    ).also { log(TAG) { "getStorageEntryLabel(): $it" } }

    fun getStorageEntryLabels(lang: String, script: String) = when {
        // https://github.com/d4rken/sdmaid-public/issues/3577
        // lge/judyln_lao_com/judyln:10/QKQ1.191222.002/2011502001bac:user/release-keys
        "en".toLang() == lang -> {
            listOf("Storage").tryPrepend { aospLabels.getStorageEntryStatic(lang, script) }
        }
        // LM-G810 10/QKQ1.200614.002
        "eu".toLang() == lang -> {
            listOf("Memoria").tryAppend { aospLabels.getStorageEntryStatic(lang, script) }
        }
        // lge/mh2lm/mh2lm:10/QKQ1.200216.002/20283101285e8:user/release-keys
        "pt".toLang() == lang -> {
            listOf("Armazenamento").tryAppend { aospLabels.getStorageEntryStatic(lang, script) }
        }

        else -> aospLabels.getStorageEntryStatic(lang, script)
    }

    fun getClearCacheLabel(): String? = context.get3rdPartyString(
        SETTINGS_PKG,
        "clear_cache_btn_text"
    ).also { log(TAG) { "getClearCacheButtonLabels(): $it" } }

    fun getClearCacheLabels(lang: String, script: String): Collection<String> = when {
        // lge/mh2lm/mh2lm:10/QKQ1.200216.002/20283101285e8:user/release-keys
        "pt".toLang() == lang -> listOf("Apagar cache").tryAppend { aospLabels.getClearCacheStatic(lang, script) }
        else -> aospLabels.getClearCacheStatic(lang, script)
    }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "LGE", "Labels")
        val SETTINGS_PKG = "com.android.settings".toPkgId()
    }
}