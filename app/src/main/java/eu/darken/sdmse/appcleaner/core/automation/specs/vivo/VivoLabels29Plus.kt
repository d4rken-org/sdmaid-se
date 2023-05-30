package eu.darken.sdmse.appcleaner.core.automation.specs.vivo

import dagger.Reusable
import eu.darken.sdmse.appcleaner.core.automation.specs.aosp.AOSPLabels14Plus
import eu.darken.sdmse.appcleaner.core.automation.specs.aosp.AOSPLabels29Plus
import eu.darken.sdmse.automation.core.common.AutomationLabelSource
import eu.darken.sdmse.common.debug.logging.logTag
import javax.inject.Inject

@Reusable
class VivoLabels29Plus @Inject constructor(
    private val aospLabels14Plus: AOSPLabels14Plus,
    private val aospLabels29Plus: AOSPLabels29Plus,
) : AutomationLabelSource {

    fun getStorageEntryDynamic(): Set<String>? = aospLabels29Plus.getStorageEntryDynamic()

    // 10: className=android.widget.LinearLayout, text=null, isClickable=true, isEnabled=true, viewIdResourceName=null, pkgName=com.android.settings
    // 14: className=android.widget.TextView, text=Internal storage, isClickable=false, isEnabled=true, viewIdResourceName=android:id/title, pkgName=com.android.settings
    fun getStorageEntryStatic(lang: String, script: String): Set<String> = when {
        "en".toLang() == lang -> setOf(
            // https://github.com/d4rken/sdmaid-public/issues/4487
            "Internal storage",
            // https://github.com/d4rken/sdmaid-public/issues/5045
            // vivo/2037M/2037:11/RP1A.200720.012/compiler0720222823:user/release-keys
            "Storage & cache",
            // vivo/1933/1933:11/RP1A.200720.012/compiler0201174227:user/release-keys
            "Storage",
        )
        // https://github.com/d4rken/sdmaid-public/issues/4758
        "in".toLang() == lang -> setOf("Penyimpanan & cache")
        else -> emptyList()
    }.tryAppend { aospLabels29Plus.getStorageEntryStatic(lang, script) }

    fun getClearCacheDynamic(): Set<String>? = aospLabels14Plus.getClearCacheDynamic()

    fun getClearCacheStatic(lang: String, script: String): Set<String> =
        aospLabels14Plus.getClearCacheStatic(lang, script)

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "Vivo", "Labels", "29Plus")
    }
}