package eu.darken.sdmse.appcleaner.core.automation.specs

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.automation.core.SpecSource
import eu.darken.sdmse.automation.core.SpecSource.Companion.getSysLocale
import eu.darken.sdmse.automation.core.crawler.*
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.pkgs.features.Installed
import timber.log.Timber
import java.util.*

abstract class AOSPBaseSpecs constructor(
    private val ipcFunnel: IPCFunnel,
    @ApplicationContext private val context: Context
) : SpecSource {

    lateinit var logTag: String

    abstract fun getStorageEntryLabels(lang: String, script: String): Collection<String>

    abstract fun getClearCacheButtonLabels(lang: String, script: String): Collection<String>

    var sourceClearCacheWindowNodeTest: suspend (Installed, Locale) -> (suspend (node: AccessibilityNodeInfo) -> Boolean)? =
        { pkgInfo: Installed, _: Locale ->
            CrawlerCommon.windowCriteriaAppIdentifier(
                AOSP_SETTINGS_PKG,
                ipcFunnel,
                pkgInfo
            )
        }

    override suspend fun getSpecs(pkg: Installed): List<ACCrawler.Step> {
        val locale = getSysLocale()
        val lang = locale.language
        val script = locale.script

        Timber.tag(logTag).v("Getting specs for %s (lang=%s, script=%s)", pkg.packageName, lang, script)

        return listOf(
            getStorageEntrySpec(pkg, locale, lang, script),
            getClearCacheEntrySpec(pkg, locale, lang, script)
        )
    }

    open suspend fun getStorageEntrySpec(pkg: Installed, locale: Locale, lang: String, script: String): ACCrawler.Step {
        var isUnsupported = false
        val storageEntryLabels = try {
            getStorageEntryLabels(lang, script)
        } catch (e: UnsupportedOperationException) {
            Timber.w("Constellation is unsupported, trying English then reporting issue.")
            isUnsupported = true
            getStorageEntryLabels("en", "")
        }

        val storageFilter = fun(node: AccessibilityNodeInfo): Boolean {
            if (!node.isTextView() || !node.idContains("android:id/title")) return false
            return node.textMatchesAny(storageEntryLabels)
        }

        return ACCrawler.Step(
            parentTag = logTag,
            pkgInfo = pkg,
            isHailMary = isUnsupported,
            label = "Find & click 'Storage' (targets=$storageEntryLabels)",
            windowIntent = CrawlerCommon.defaultWindowIntent(context, pkg),
            windowEventFilter = CrawlerCommon.defaultWindowFilter(AOSP_SETTINGS_PKG),
            windowNodeTest = CrawlerCommon.windowCriteriaAppIdentifier(AOSP_SETTINGS_PKG, ipcFunnel, pkg),
            nodeTest = storageFilter,
            nodeRecovery = CrawlerCommon.getDefaultNodeRecovery(pkg),
            nodeMapping = CrawlerCommon.clickableParent(),
            action = CrawlerCommon.defaultClick()
        )
    }

    open suspend fun getClearCacheEntrySpec(
        pkg: Installed,
        locale: Locale,
        lang: String,
        script: String
    ): ACCrawler.Step {
        var isUnsupported = false
        val clearCacheButtonLabels = try {
            getClearCacheButtonLabels(lang, script)
        } catch (e: UnsupportedOperationException) {
            Timber.w("Constellation is unsupported, trying English then reporting issue.")
            isUnsupported = true
            getClearCacheButtonLabels("en", "")
        }

        val buttonFilter = fun(node: AccessibilityNodeInfo): Boolean {
            if (!node.isClickyButton()) return false
            return node.textMatchesAny(clearCacheButtonLabels)
        }

        return ACCrawler.Step(
            parentTag = logTag,
            pkgInfo = pkg,
            isHailMary = isUnsupported,
            label = "Find & click 'Clear Cache' (targets=$clearCacheButtonLabels)",
            windowNodeTest = sourceClearCacheWindowNodeTest(pkg, locale),
            nodeTest = buttonFilter,
            action = getDefaultClearCacheClick(pkg, logTag)
        )
    }

    companion object {
        const val AOSP_SETTINGS_PKG = "com.android.settings"
    }
}