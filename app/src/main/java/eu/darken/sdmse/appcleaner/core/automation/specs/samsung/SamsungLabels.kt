package eu.darken.sdmse.appcleaner.core.automation.specs.samsung

import dagger.Reusable
import eu.darken.sdmse.automation.core.common.AutomationLabelSource
import eu.darken.sdmse.common.BuildWrap
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.hasApiLevel
import javax.inject.Inject

@Reusable
open class SamsungLabels @Inject constructor(
    private val labels14Plus: SamsungLabels14Plus,
    private val labels29Plus: SamsungLabels29Plus,
) : AutomationLabelSource {

    fun getStorageEntryDynamic(): Set<String> = when {
        hasApiLevel(29) -> labels29Plus.getStorageEntryDynamic()
        hasApiLevel(14) -> labels14Plus.getStorageEntryDynamic()
        else -> throw UnsupportedOperationException("Api level not supported: ${BuildWrap.VERSION.SDK_INT}")
    }

    fun getStorageEntryLabels(lang: String, script: String): Set<String> = when {
        hasApiLevel(29) -> labels29Plus.getStorageEntryLabels(lang, script)
        hasApiLevel(14) -> labels14Plus.getStorageEntryLabels(lang, script)
        else -> throw UnsupportedOperationException("Api level not supported: ${BuildWrap.VERSION.SDK_INT}")
    }

    fun getClearCacheDynamic(): Set<String> = when {
        hasApiLevel(29) -> labels29Plus.getClearCacheDynamic()
        hasApiLevel(14) -> labels14Plus.getClearCacheDynamic()
        else -> throw UnsupportedOperationException("Api level not supported: ${BuildWrap.VERSION.SDK_INT}")
    }

    fun getClearCacheLabels(lang: String, script: String): Set<String> = when {
        hasApiLevel(29) -> labels29Plus.getClearCacheLabels(lang, script)
        hasApiLevel(14) -> labels14Plus.getClearCacheLabels(lang, script)
        else -> throw UnsupportedOperationException("Api level not supported: ${BuildWrap.VERSION.SDK_INT}")
    }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "Samsung", "Labels")
    }
}