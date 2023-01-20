package eu.darken.sdmse.appcleaner.core.forensics.filter

import eu.darken.sdmse.appcleaner.core.forensics.*
import eu.darken.sdmse.common.areas.DataArea.Type.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WebViewCacheFilterTest : BaseFilterTest() {

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = WebViewCacheFilter(
        jsonBasedSieveFactory = createJsonSieveFactory()
    )

    @Test fun testGeneral() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("eu.thedarken.sdm.test")
                .prefixFree("eu.thedarken.sdm.test/files/.Fabric/io.fabric.sdk.android:fabric/com.crashlytics.settings.json")
                .locs(SDCARD, PUBLIC_DATA, PRIVATE_DATA)
        )
        addCandidate(
            neg().pkgs("eu.thedarken.sdm.test")
                .prefixFree("eu.thedarken.sdm.test/files/.Fabric/io.fabric.sdk.android:fabric")
                .locs(SDCARD, PUBLIC_DATA, PRIVATE_DATA)
        )
        addCandidate(
            neg().pkgs("eu.thedarken.sdm.test").prefixFree("eu.thedarken.sdm.test/files/.Fabric")
                .locs(SDCARD, PUBLIC_DATA, PRIVATE_DATA)
        )
        addCandidate(
            neg().pkgs("eu.thedarken.sdm.test").prefixFree("eu.thedarken.sdm.test/files")
                .locs(SDCARD, PUBLIC_DATA, PRIVATE_DATA)
        )
        addCandidate(
            neg().pkgs("eu.thedarken.sdm.test").prefixFree("eu.thedarken.sdm.test/app_webview/Cache")
                .locs(SDCARD, PUBLIC_DATA, PRIVATE_DATA)
        )
        addCandidate(
            pos().pkgs("eu.thedarken.sdm.test").prefixFree("eu.thedarken.sdm.test/app_webview/Cache/asdkjalsjdlasdasd")
                .locs(SDCARD, PUBLIC_DATA, PRIVATE_DATA)
        )
        addCandidate(
            pos().pkgs("eu.thedarken.sdm.test")
                .prefixFree("eu.thedarken.sdm.test/app_webview/Cache/asdkjalsjdlasdasd/asdasd/Cache")
                .locs(SDCARD, PUBLIC_DATA, PRIVATE_DATA)
        )
        confirm(create())
    }

    @Test fun testAmazon() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("com.amazon.mShop.android.shopping")
                .prefixFree("com.amazon.mShop.android.shopping/app_mashWebViewState")
                .locs(PUBLIC_DATA, PRIVATE_DATA)
        )
        addCandidate(
            pos().pkgs("com.amazon.mShop.android.shopping")
                .prefixFree("com.amazon.mShop.android.shopping/app_mashWebViewState/1531151908029MASHWebFragment1.0")
                .locs(PUBLIC_DATA, PRIVATE_DATA)
        )
        addCandidate(
            pos().pkgs("com.amazon.mShop.android.shopping")
                .prefixFree("com.amazon.mShop.android.shopping/app_mashWebViewState/asdasd/Cache")
                .locs(PUBLIC_DATA, PRIVATE_DATA)
        )
        confirm(create())
    }

    @Test fun testPuffin() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("com.cloudmosa.puffin").prefixFree("com.cloudmosa.puffin/app_favicon_cache")
                .locs(PUBLIC_DATA, PRIVATE_DATA)
        )
        addCandidate(
            pos().pkgs("com.cloudmosa.puffin").prefixFree("com.cloudmosa.puffin/app_favicon_cache/html5test.com")
                .locs(PUBLIC_DATA, PRIVATE_DATA)
        )
        addCandidate(
            pos().pkgs("com.cloudmosa.puffin").prefixFree("com.cloudmosa.puffin/app_favicon_cache/something/else")
                .locs(PUBLIC_DATA, PRIVATE_DATA)
        )
        addCandidate(
            neg().pkgs("com.cloudmosa.puffin").prefixFree("com.cloudmosa.puffin/app_favicon_cache/something/.nomedia")
                .locs(PUBLIC_DATA, PRIVATE_DATA)
        )
        confirm(create())
    }
}