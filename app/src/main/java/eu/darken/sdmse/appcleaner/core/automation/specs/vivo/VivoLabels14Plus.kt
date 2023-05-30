package eu.darken.sdmse.appcleaner.core.automation.specs.vivo

import dagger.Reusable
import eu.darken.sdmse.appcleaner.core.automation.specs.aosp.AOSPLabels14Plus
import eu.darken.sdmse.automation.core.common.AutomationLabelSource
import eu.darken.sdmse.common.debug.logging.logTag
import javax.inject.Inject

@Reusable
class VivoLabels14Plus @Inject constructor(
    private val labels14Plus: AOSPLabels14Plus,
) : AutomationLabelSource {

    fun getStorageEntryDynamic(): Set<String>? = labels14Plus.getStorageEntryDynamic()

    fun getStorageEntryStatic(lang: String, script: String): Set<String> =
        labels14Plus.getStorageEntryStatic(lang, script)

    fun getClearCacheDynamic(): Set<String>? = labels14Plus.getClearCacheDynamic()

    fun getClearCacheStatic(lang: String, script: String): Set<String> = when {
        // vivo/1808/1808:8.1.0/O11019/1592484538:user/release-keys
        // https://github.com/d4rken/sdmaid-public/issues/3670
        "in".toLang() == lang -> setOf("Bersihkan cache")
        else -> emptyList()
    }.tryAppend { labels14Plus.getClearCacheStatic(lang, script) }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "Vivo", "Labels", "14Plus")
    }
}