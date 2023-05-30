package eu.darken.sdmse.appcleaner.core.automation.specs.vivo

import dagger.Reusable
import eu.darken.sdmse.automation.core.common.AutomationLabelSource
import eu.darken.sdmse.common.BuildWrap
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.hasApiLevel
import javax.inject.Inject

@Reusable
open class VivoLabels @Inject constructor(
    private val vivoLabels14Plus: VivoLabels14Plus,
    private val vivoLabels29Plus: VivoLabels29Plus,
) : AutomationLabelSource {

    fun getStorageEntryDynamic(): Set<String>? = when {
        hasApiLevel(29) -> vivoLabels29Plus.getStorageEntryDynamic()
        hasApiLevel(14) -> vivoLabels14Plus.getStorageEntryDynamic()
        else -> throw UnsupportedOperationException("Api level not supported: ${BuildWrap.VERSION.SDK_INT}")
    }

    fun getStorageEntryStatic(lang: String, script: String): Set<String> = when {
        hasApiLevel(29) -> vivoLabels29Plus.getStorageEntryStatic(lang, script)
        hasApiLevel(14) -> vivoLabels14Plus.getStorageEntryStatic(lang, script)
        else -> throw UnsupportedOperationException("Api level not supported: ${BuildWrap.VERSION.SDK_INT}")
    }

    fun getClearCacheDynamic(): Set<String>? = when {
        hasApiLevel(29) -> vivoLabels29Plus.getClearCacheDynamic()
        hasApiLevel(14) -> vivoLabels14Plus.getClearCacheDynamic()
        else -> throw UnsupportedOperationException("Api level not supported: ${BuildWrap.VERSION.SDK_INT}")
    }

    fun getClearCacheStatic(lang: String, script: String): Set<String> = when {
        hasApiLevel(29) -> vivoLabels29Plus.getClearCacheStatic(lang, script)
        hasApiLevel(14) -> vivoLabels14Plus.getClearCacheStatic(lang, script)
        else -> throw UnsupportedOperationException("Api level not supported: ${BuildWrap.VERSION.SDK_INT}")
    }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "Vivo", "Labels")
    }
}