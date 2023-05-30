package eu.darken.sdmse.appcleaner.core.automation.specs.oneplus

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
class OnePlusLabels14Plus @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aospLabels14Plus: AOSPLabels14Plus,
) : AutomationLabelSource {


    fun getStorageEntryLabel(): String? = context.get3rdPartyString(
        SETTINGS_PKG,
        "storage_settings"
    ).also { log(TAG) { "getStorageEntryLabel(): $it" } }

    fun getStorageEntryLabels(lang: String, script: String): Collection<String> =
        aospLabels14Plus.getStorageEntryStatic(lang, script)

    fun getClearCacheDynamic(): Set<String>? = aospLabels14Plus.getClearCacheDynamic()

    fun getClearCacheStatic(lang: String, script: String): Set<String> =
        aospLabels14Plus.getClearCacheStatic(lang, script)

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "OnePlus", "Labels", "14Plus")
        val SETTINGS_PKG = "com.android.settings".toPkgId()
    }

}
