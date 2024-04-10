package eu.darken.sdmse.appcleaner.core.automation.specs.oneplus

import dagger.Reusable
import eu.darken.sdmse.appcleaner.core.automation.specs.AppCleanerLabelSource
import eu.darken.sdmse.common.BuildWrap
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.hasApiLevel
import javax.inject.Inject

@Reusable
open class OnePlusLabels @Inject constructor(
    private val labels14Plus: OnePlusLabels14Plus,
    private val labels29Plus: OnePlusLabels29Plus,
    private val labels31Plus: OnePlusLabels31Plus,
) : AppCleanerLabelSource {

    fun getStorageEntryDynamic(): Set<String> = when {
        hasApiLevel(31) -> labels31Plus.getStorageEntryDynamic()
        hasApiLevel(29) -> labels29Plus.getStorageEntryDynamic()
        hasApiLevel(14) -> labels14Plus.getStorageEntryDynamic()
        else -> throw UnsupportedOperationException("Api level not supported: ${BuildWrap.VERSION.SDK_INT}")
    }

    fun getStorageEntryLabels(lang: String, script: String): Set<String> = when {
        hasApiLevel(31) -> labels31Plus.getStorageEntryLabels(lang, script)
        hasApiLevel(29) -> labels29Plus.getStorageEntryLabels(lang, script)
        hasApiLevel(14) -> labels14Plus.getStorageEntryLabels(lang, script)
        else -> throw UnsupportedOperationException("Api level not supported: ${BuildWrap.VERSION.SDK_INT}")
    }

    fun getClearCacheDynamic(): Set<String> = when {
        hasApiLevel(31) -> labels31Plus.getClearCacheDynamic()
        hasApiLevel(29) -> labels29Plus.getClearCacheDynamic()
        hasApiLevel(14) -> labels14Plus.getClearCacheDynamic()
        else -> throw UnsupportedOperationException("Api level not supported: ${BuildWrap.VERSION.SDK_INT}")
    }

    fun getClearCacheStatic(lang: String, script: String): Set<String> = when {
        hasApiLevel(31) -> labels31Plus.getClearCacheStatic(lang, script)
        hasApiLevel(29) -> labels29Plus.getClearCacheStatic(lang, script)
        hasApiLevel(14) -> labels14Plus.getClearCacheStatic(lang, script)
        else -> throw UnsupportedOperationException("Api level not supported: ${BuildWrap.VERSION.SDK_INT}")
    }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "OnePlus", "Labels")
    }
}