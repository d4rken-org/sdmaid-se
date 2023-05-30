package eu.darken.sdmse.appcleaner.core.automation.specs.aosp

import dagger.Reusable
import eu.darken.sdmse.automation.core.common.AutomationLabelSource
import eu.darken.sdmse.common.BuildWrap
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.hasApiLevel
import javax.inject.Inject

@Reusable
class AOSPLabels @Inject constructor(
    private val labels14Plus: AOSPLabels14Plus,
    private val labels29Plus: AOSPLabels29Plus,
) : AutomationLabelSource {

    fun getStorageEntryDynamic(): Set<String>? = when {
        hasApiLevel(29) -> labels29Plus.getStorageEntryDynamic()
        hasApiLevel(14) -> labels14Plus.getStorageEntryDynamic()
        else -> throw UnsupportedOperationException("Api level not supported: ${BuildWrap.VERSION.SDK_INT}")
    }

    fun getStorageEntryStatic(lang: String, script: String): Set<String> = when {
        hasApiLevel(29) -> labels29Plus.getStorageEntryStatic(lang, script)
        hasApiLevel(14) -> labels14Plus.getStorageEntryStatic(lang, script)
        else -> throw UnsupportedOperationException("Api level not supported: ${BuildWrap.VERSION.SDK_INT}")
    }

    fun getClearCacheDynamic(): Set<String>? = when {
        hasApiLevel(29) -> labels29Plus.getClearCacheDynamic()
        hasApiLevel(14) -> labels14Plus.getClearCacheDynamic()
        else -> throw UnsupportedOperationException("Api level not supported: ${BuildWrap.VERSION.SDK_INT}")
    }

    fun getClearCacheStatic(lang: String, script: String): Set<String> = when {
        hasApiLevel(29) -> labels29Plus.getClearCacheStatic(lang, script)
        hasApiLevel(14) -> labels14Plus.getClearCacheStatic(lang, script)
        else -> throw UnsupportedOperationException("Api level not supported: ${BuildWrap.VERSION.SDK_INT}")
    }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "AOSP", "Labels")
    }
}