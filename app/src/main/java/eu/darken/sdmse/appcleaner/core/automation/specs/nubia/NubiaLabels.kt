package eu.darken.sdmse.appcleaner.core.automation.specs.nubia

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
class NubiaLabels @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aospLabels: AOSPLabels,
) : AutomationLabelSource {

    fun getStorageEntryLabel(): String? = context.get3rdPartyString(
        SETTINGS_PKG,
        "storage_settings_for_app"
    ).also { log(TAG) { "getStorageEntryLabel(): $it" } }

    fun getStorageEntryLabels(lang: String, script: String) = when {
        "es".toLang() == lang -> setOf("Almacenamiento")
        else -> aospLabels.getStorageEntryStatic(lang, script)
    }.tryAppend { aospLabels.getStorageEntryStatic(lang, script) }


    fun getClearCacheLabel(): String? = context.get3rdPartyString(
        SETTINGS_PKG,
        "clear_cache_btn_text"
    ).also { log(TAG) { "getClearCacheButtonLabels(): $it" } }

    fun getClearCacheLabels(lang: String, script: String): Collection<String> =
        aospLabels.getStorageEntryStatic(lang, script)

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "Nubia", "Labels")
        val SETTINGS_PKG = "com.android.settings".toPkgId()
    }
}