package eu.darken.sdmse.appcleaner.core.forensics.filter

import eu.darken.sdmse.appcleaner.core.forensics.BaseFilterTest
import eu.darken.sdmse.appcleaner.core.forensics.neg
import eu.darken.sdmse.appcleaner.core.forensics.pos
import eu.darken.sdmse.common.areas.DataArea.Type.PRIVATE_DATA
import eu.darken.sdmse.common.areas.DataArea.Type.PUBLIC_DATA
import eu.darken.sdmse.common.areas.DataArea.Type.SDCARD
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
        jsonBasedSieveFactory = createJsonSieveFactory(),
        gatewaySwitch = gatewaySwitch,
    )

    @Test fun `test general`() = runTest {
        addDefaultNegatives()
        neg(
            "eu.thedarken.sdm.test",
            SDCARD,
            "eu.thedarken.sdm.test/files/.Fabric/io.fabric.sdk.android:fabric/com.crashlytics.settings.json"
        )
        neg(
            "eu.thedarken.sdm.test",
            PUBLIC_DATA,
            "eu.thedarken.sdm.test/files/.Fabric/io.fabric.sdk.android:fabric/com.crashlytics.settings.json"
        )
        neg(
            "eu.thedarken.sdm.test",
            PRIVATE_DATA,
            "eu.thedarken.sdm.test/files/.Fabric/io.fabric.sdk.android:fabric/com.crashlytics.settings.json"
        )

        neg("eu.thedarken.sdm.test", SDCARD, "eu.thedarken.sdm.test/files/.Fabric/io.fabric.sdk.android:fabric")
        neg("eu.thedarken.sdm.test", PUBLIC_DATA, "eu.thedarken.sdm.test/files/.Fabric/io.fabric.sdk.android:fabric")
        neg("eu.thedarken.sdm.test", PRIVATE_DATA, "eu.thedarken.sdm.test/files/.Fabric/io.fabric.sdk.android:fabric")

        neg("eu.thedarken.sdm.test", SDCARD, "eu.thedarken.sdm.test/files/.Fabric")
        neg("eu.thedarken.sdm.test", PUBLIC_DATA, "eu.thedarken.sdm.test/files/.Fabric")
        neg("eu.thedarken.sdm.test", PRIVATE_DATA, "eu.thedarken.sdm.test/files/.Fabric")

        neg("eu.thedarken.sdm.test", SDCARD, "eu.thedarken.sdm.test/files")
        neg("eu.thedarken.sdm.test", PUBLIC_DATA, "eu.thedarken.sdm.test/files")
        neg("eu.thedarken.sdm.test", PRIVATE_DATA, "eu.thedarken.sdm.test/files")

        neg("eu.thedarken.sdm.test", SDCARD, "eu.thedarken.sdm.test/app_webview/Cache")
        neg("eu.thedarken.sdm.test", PUBLIC_DATA, "eu.thedarken.sdm.test/app_webview/Cache")
        neg("eu.thedarken.sdm.test", PRIVATE_DATA, "eu.thedarken.sdm.test/app_webview/Cache")

        pos("eu.thedarken.sdm.test", SDCARD, "eu.thedarken.sdm.test/app_webview/Cache/asdkjalsjdlasdasd")
        pos("eu.thedarken.sdm.test", PUBLIC_DATA, "eu.thedarken.sdm.test/app_webview/Cache/asdkjalsjdlasdasd")
        pos("eu.thedarken.sdm.test", PRIVATE_DATA, "eu.thedarken.sdm.test/app_webview/Cache/asdkjalsjdlasdasd")

        pos("eu.thedarken.sdm.test", SDCARD, "eu.thedarken.sdm.test/app_webview/Cache/asdkjalsjdlasdasd/asdasd/Cache")
        pos(
            "eu.thedarken.sdm.test",
            PUBLIC_DATA,
            "eu.thedarken.sdm.test/app_webview/Cache/asdkjalsjdlasdasd/asdasd/Cache"
        )
        pos(
            "eu.thedarken.sdm.test",
            PRIVATE_DATA,
            "eu.thedarken.sdm.test/app_webview/Cache/asdkjalsjdlasdasd/asdasd/Cache"
        )
        
        confirm(create())
    }

    @Test fun `test amazon`() = runTest {
        addDefaultNegatives()
        neg("com.amazon.mShop.android.shopping", PUBLIC_DATA, "com.amazon.mShop.android.shopping/app_mashWebViewState")
        neg("com.amazon.mShop.android.shopping", PRIVATE_DATA, "com.amazon.mShop.android.shopping/app_mashWebViewState")

        pos(
            "com.amazon.mShop.android.shopping",
            PUBLIC_DATA,
            "com.amazon.mShop.android.shopping/app_mashWebViewState/1531151908029MASHWebFragment1.0"
        )
        pos(
            "com.amazon.mShop.android.shopping",
            PRIVATE_DATA,
            "com.amazon.mShop.android.shopping/app_mashWebViewState/1531151908029MASHWebFragment1.0"
        )

        pos(
            "com.amazon.mShop.android.shopping",
            PUBLIC_DATA,
            "com.amazon.mShop.android.shopping/app_mashWebViewState/asdasd/Cache"
        )
        pos(
            "com.amazon.mShop.android.shopping",
            PRIVATE_DATA,
            "com.amazon.mShop.android.shopping/app_mashWebViewState/asdasd/Cache"
        )
        
        confirm(create())
    }

    @Test fun `test puffin`() = runTest {
        addDefaultNegatives()
        neg("com.cloudmosa.puffin", PUBLIC_DATA, "com.cloudmosa.puffin/app_favicon_cache")
        neg("com.cloudmosa.puffin", PRIVATE_DATA, "com.cloudmosa.puffin/app_favicon_cache")

        pos("com.cloudmosa.puffin", PUBLIC_DATA, "com.cloudmosa.puffin/app_favicon_cache/html5test.com")
        pos("com.cloudmosa.puffin", PRIVATE_DATA, "com.cloudmosa.puffin/app_favicon_cache/html5test.com")

        pos("com.cloudmosa.puffin", PUBLIC_DATA, "com.cloudmosa.puffin/app_favicon_cache/something/else")
        pos("com.cloudmosa.puffin", PRIVATE_DATA, "com.cloudmosa.puffin/app_favicon_cache/something/else")

        neg("com.cloudmosa.puffin", PUBLIC_DATA, "com.cloudmosa.puffin/app_favicon_cache/something/.nomedia")
        neg("com.cloudmosa.puffin", PRIVATE_DATA, "com.cloudmosa.puffin/app_favicon_cache/something/.nomedia")
        
        confirm(create())
    }
}