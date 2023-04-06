package eu.darken.sdmse.appcleaner.core.forensics.filter

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.appcleaner.core.AppCleanerSettings
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.appcleaner.core.forensics.sieves.json.JsonBasedSieve
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.*
import eu.darken.sdmse.common.pkgs.Pkg
import java.util.*
import javax.inject.Inject
import javax.inject.Provider


@Reusable
class WebViewCacheFilter @Inject constructor(
    private val jsonBasedSieveFactory: JsonBasedSieve.Factory
) : ExpendablesFilter {

    private lateinit var sieve: JsonBasedSieve

    override suspend fun initialize() {
        log(TAG) { "initialize()" }
        sieve = jsonBasedSieveFactory.create("expendables/db_webcaches.json")
    }

    override suspend fun isExpendable(
        pkgId: Pkg.Id,
        target: APathLookup<APath>,
        areaType: DataArea.Type,
        segments: Segments
    ): Boolean {
        if (segments.isNotEmpty() && IGNORED_FILES.contains(segments[segments.size - 1])) {
            return false
        }

        if (WEBVIEW_CACHES.any { it.prepend(pkgId.name).isAncestorOf(segments) }) {
            return true
        }

        return segments.isNotEmpty() && sieve.matches(pkgId, areaType, segments)
    }

    @Reusable
    class Factory @Inject constructor(
        private val settings: AppCleanerSettings,
        private val filterProvider: Provider<WebViewCacheFilter>
    ) : ExpendablesFilter.Factory {
        override suspend fun isEnabled(): Boolean = settings.filterWebviewEnabled.value()
        override suspend fun create(): ExpendablesFilter = filterProvider.get()
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Factory): ExpendablesFilter.Factory
    }

    companion object {
        private val WEBVIEW_CACHES = listOf(
            "app_webview/Cache",
            "app_webview/Application Cache",
            "app_webview/Service Worker/CacheStorage",
            "app_webview/Service Worker/ScriptCache",
            "app_webview/GPUCache",
            "app_chrome/ShaderCache",
            "app_chrome/GrShaderCache",
            "app_chrome/Default/Application Cache",
            "app_chrome/Default/Service Worker/CacheStorage",
            "app_chrome/Default/Service Worker/ScriptCache",
            "app_chrome/Default/GPUCache"
        ).map { it.toSegs() }
        private val IGNORED_FILES: Collection<String> = listOf(
            ".nomedia"
        )
        private val TAG = logTag("AppCleaner", "Scanner", "Filter", "Webview")
    }
}