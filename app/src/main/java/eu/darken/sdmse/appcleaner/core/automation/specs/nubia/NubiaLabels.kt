package eu.darken.sdmse.appcleaner.core.automation.specs.nubia

import android.content.Context
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.appcleaner.core.automation.specs.AppCleanerLabelSource
import eu.darken.sdmse.appcleaner.core.automation.specs.aosp.AOSPLabels
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.toPkgId
import javax.inject.Inject

@Suppress("IntroduceWhenSubject")
@Reusable
class NubiaLabels @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aospLabels: AOSPLabels,
) : AppCleanerLabelSource {

    fun getStorageEntryDynamic(): Set<String> = setOf(
        "storage_settings_for_app"
    ).getAsStringResources(context, SETTINGS_PKG)

    fun getStorageEntryLabels(lang: String, script: String) = when {
        "es".toLang() == lang -> setOf("Almacenamiento")
        else -> aospLabels.getStorageEntryStatic(lang, script)
    }.tryAppend { aospLabels.getStorageEntryStatic(lang, script) }


    fun getClearCacheDynamic(): Set<String> = setOf(
        "clear_cache_btn_text"
    ).getAsStringResources(context, SETTINGS_PKG)

    fun getClearCacheLabels(lang: String, script: String): Collection<String> =
        aospLabels.getStorageEntryStatic(lang, script)

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "Nubia", "Labels")
        val SETTINGS_PKG = "com.android.settings".toPkgId()
    }
}