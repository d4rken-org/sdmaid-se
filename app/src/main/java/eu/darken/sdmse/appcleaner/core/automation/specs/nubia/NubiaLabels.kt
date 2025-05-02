package eu.darken.sdmse.appcleaner.core.automation.specs.nubia

import dagger.Reusable
import eu.darken.sdmse.appcleaner.core.automation.specs.AppCleanerLabelSource
import eu.darken.sdmse.appcleaner.core.automation.specs.aosp.AOSPLabels
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.automation.core.specs.getLocales
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.toPkgId
import javax.inject.Inject

@Suppress("IntroduceWhenSubject")
@Reusable
class NubiaLabels @Inject constructor(
    private val aospLabels: AOSPLabels,
) : AppCleanerLabelSource {

    fun getStorageEntryDynamic(
        acsContext: AutomationExplorer.Context
    ): Set<String> = acsContext.getStrings(SETTINGS_PKG, setOf("storage_settings_for_app"))

    fun getStorageEntryLabels(acsContext: AutomationExplorer.Context) = acsContext.getLocales()
        .map { it.language to it.script }
        .mapNotNull { (lang, _) ->
            when {
                "es".toLang() == lang -> setOf("Almacenamiento")
                else -> null
            }
        }
        .flatten()
        .append { aospLabels.getStorageEntryStatic(acsContext) }
        .toSet()

    fun getClearCacheDynamic(
        acsContext: AutomationExplorer.Context
    ): Set<String> = acsContext.getStrings(SETTINGS_PKG, setOf("clear_cache_btn_text"))

    fun getClearCacheLabels(acsContext: AutomationExplorer.Context): Collection<String> =
        aospLabels.getStorageEntryStatic(acsContext)

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "Nubia", "Labels")
        val SETTINGS_PKG = "com.android.settings".toPkgId()
    }
}