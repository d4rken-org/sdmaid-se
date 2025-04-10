package eu.darken.sdmse.appcleaner.core.forensics.filter

import eu.darken.sdmse.appcleaner.core.forensics.BaseFilterTest
import eu.darken.sdmse.appcleaner.core.forensics.neg
import eu.darken.sdmse.appcleaner.core.forensics.pos
import eu.darken.sdmse.common.areas.DataArea.Type.PRIVATE_DATA
import eu.darken.sdmse.common.areas.DataArea.Type.PUBLIC_DATA
import eu.darken.sdmse.common.areas.DataArea.Type.PUBLIC_OBB
import eu.darken.sdmse.common.areas.DataArea.Type.SDCARD
import eu.darken.sdmse.common.rngString
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AdvertisementFilterTest : BaseFilterTest() {

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = AdvertisementFilter(
        jsonBasedSieveFactory = createJsonSieveFactory(),
        environment = storageEnvironment,
        gatewaySwitch = gatewaySwitch,
    )

    @Test fun `test analytics filter mologiq`() = runTest {
        addDefaultNegatives()
        neg(testPkg, PRIVATE_DATA, "eu.thedarken.sdm.test/files")
        neg(testPkg, PRIVATE_DATA, "eu.thedarken.sdm.test/files/.something.mologiq")
        neg(testPkg, PRIVATE_DATA, "eu.thedarken.sdm.test/files/.abcedefg.mologiq")
        neg(testPkg, PRIVATE_DATA, "eu.thedarken.sdm.test/databases")
        neg(testPkg, PRIVATE_DATA, "eu.thedarken.sdm.test/databases/mologiq_")
        neg(testPkg, PRIVATE_DATA, "eu.thedarken.sdm.test/files/item/.b0b0bf57-012d-4b0f-8266-1ca07820a91a.mologiq")
        neg(testPkg, PRIVATE_DATA, "eu.thedarken.sdm.test/databases/item/mologiq")
        pos(testPkg, PRIVATE_DATA, "eu.thedarken.sdm.test/files/.b0b0bf57-012d-4b0f-8266-1ca07820a91a.mologiq")
        pos(testPkg, PRIVATE_DATA, "eu.thedarken.sdm.test/files/.e3883dc0-5bd6-4b93-840a-5d95d788a87e.mologiq")
        pos(testPkg, PRIVATE_DATA, "eu.thedarken.sdm.test/files/.13a5fef7-518e-4a61-b856-5ae5a8701da0.mologiq")
        pos(testPkg, PRIVATE_DATA, "eu.thedarken.sdm.test/databases/mologiq")
        confirm(create())
    }

    @Test fun `test vulge`() = runTest {
        addDefaultNegatives()
        neg(testPkg, PUBLIC_DATA, "eu.thedarken.sdm.test/files/.vungleabc")
        neg(testPkg, PUBLIC_DATA, "eu.thedarken.sdm.test/.vungle")
        neg(testPkg, PUBLIC_DATA, "eu.thedarken.sdm.test/files/abc.vungleabc")
        pos(testPkg, PUBLIC_DATA, "eu.thedarken.sdm.test/files/.vungle")
        pos(testPkg, PUBLIC_DATA, "eu.thedarken.sdm.test/files/.vungle/$rngString")
        confirm(create())
    }

    @Test fun `wechat ads`() = runTest {
        addDefaultNegatives()

        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/sns_ad_landingpages")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/sns_ad_landingpages/.nomedia")
        pos("com.tencent.mm", SDCARD, "tencent/MicroMsg/sns_ad_landingpages/$rngString")

        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/hbstoryvideo")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/hbstoryvideo/.nomedia")
        pos("com.tencent.mm", SDCARD, "tencent/MicroMsg/hbstoryvideo/$rngString")

        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/sns_ad_landingpages")
        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/sns_ad_landingpages/.nomedia")
        pos("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/sns_ad_landingpages/$rngString")

        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/hbstoryvideo")
        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/hbstoryvideo/.nomedia")
        pos("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/hbstoryvideo/$rngString")

        confirm(create())
    }

    @Test fun `test midas oversea`() = runTest {
        addDefaultNegatives()
        neg("com.vng.pubgmobile", SDCARD, "MidasOversea")
        neg("com.vng.pubgmobile", SDCARD, "MidasOversea/.nomedia")
        pos("com.vng.pubgmobile", SDCARD, "MidasOversea/$rngString")
        neg("com.tencent.mm", SDCARD, "MidasOversea")
        neg("com.tencent.mm", SDCARD, "MidasOversea/.nomedia")
        pos("com.tencent.mm", SDCARD, "MidasOversea/$rngString")
        confirm(create())
    }

    @Test fun `test vungle cache`() = runTest {
        addDefaultNegatives()
        neg("com.sega.sonicboomandroid", PUBLIC_DATA, "com.sega.sonicboomandroid/files")
        neg("com.sega.sonicboomandroid", PUBLIC_DATA, "com.sega.sonicboomandroid/files/vungle_ca")
        pos("com.sega.sonicboomandroid", PUBLIC_DATA, "com.sega.sonicboomandroid/files/vungle_cache")
        pos("com.sega.sonicboomandroid", PUBLIC_DATA, "com.sega.sonicboomandroid/files/vungle_cache/$rngString")

        // https://github.com/d4rken/sdmaid-public/issues/5485
        neg("com.sega.sonicboomandroid", PUBLIC_DATA, "com.sega.sonicboomandroid/no_backup")
        pos("com.sega.sonicboomandroid", PUBLIC_DATA, "com.sega.sonicboomandroid/no_backup/vungle_cache/$rngString")
        confirm(create())
    }

    @Test fun `test meizu media`() = runTest {
        neg("com.meizu.media.video", PUBLIC_DATA, "com.meizu.media.video/MzAdLog")
        pos("com.meizu.media.video", PUBLIC_DATA, "com.meizu.media.video/MzAdLog/something")
        confirm(create())
    }

    @Test fun `test z camera`() = runTest {
        neg("com.jb.zcamera", PUBLIC_OBB, "com.jb.zcamera/GoAdSdk")
        pos("com.jb.zcamera", PUBLIC_OBB, "com.jb.zcamera/GoAdSdk/advert")
        pos("com.jb.zcamera", PUBLIC_OBB, "com.jb.zcamera/GoAdSdk/advert/cacheFile")
        confirm(create())
    }

    @Test fun `general GoAdSdk test`() = runTest {
        neg(testPkg, PUBLIC_OBB, "com.jb.beautycam/GoAdSdk")
        pos(testPkg, PUBLIC_OBB, "com.jb.beautycam/GoAdSdk/advert")
        pos(testPkg, PUBLIC_OBB, "com.jb.beautycam/GoAdSdk/advert/cacheFile")
        confirm(create())
    }

    @Test fun `face editor`() = runTest {
        neg("com.scoompa.faceeditor", PUBLIC_DATA, "com.scoompa.faceeditor/files/ads")
        pos("com.scoompa.faceeditor", PUBLIC_DATA, "com.scoompa.faceeditor/files/ads/adfile")
        confirm(create())
    }

    @Test fun `test touchpal ad cache`() = runTest {
        val pkgs = arrayOf(
            "com.cootek.smartinputv5",
            "com.cootek.smartinputv5.oem",
            "com.emoji.keyboard.touchpal",
            "com.keyboard.cb.oem"
        )
        pkgs.forEach { pkg ->
            neg(pkg, SDCARD, "TouchPal2015/plugin_cache")
            pos(pkg, SDCARD, "TouchPal2015/plugin_cache/theCakeIsALie")
        }
        confirm(create())
    }

    @Test fun `test meizu file manager`() = runTest {
        neg("com.meizu.filemanager", PUBLIC_DATA, "com.meizu.filemanager/update_component")
        pos("com.meizu.filemanager", PUBLIC_DATA, "com.meizu.filemanager/update_component_log")
        pos("com.meizu.filemanager", PUBLIC_DATA, "com.meizu.filemanager/update_component_log123")
        confirm(create())
    }

    @Test fun `test video like`() = runTest {
        addDefaultNegatives()
        neg("video.like", SDCARD, "._sdk_ruui")
        neg("video.like", SDCARD, "_sdk_ruuid")
        pos("video.like", SDCARD, "._sdk_ruuid")
        confirm(create())
    }

    @Test fun `appo deal`() = runTest {
        addDefaultNegatives()
        neg("com.ludashi.dualspace", SDCARD, ".appodea")
        neg("com.ludashi.dualspace", SDCARD, ".appodeall")
        pos("com.ludashi.dualspace", SDCARD, ".appodeal")
        confirm(create())
    }

    @Test fun `test que video`() = runTest {
        addDefaultNegatives()
        neg("com.quvideo.xiaoying", SDCARD, "data/.push")
        pos("com.quvideo.xiaoying", SDCARD, "data/.push_deviceid")
        confirm(create())
    }

    @Test fun `miui ad preload`() = runTest {
        addDefaultNegatives()
        neg("com.miui.msa.global", PUBLIC_DATA, "com.miui.msa.global/filespush_ad_preload")
        neg("com.miui.msa.global", PUBLIC_DATA, "com.miui.msa.global/filessplash_preload")
        pos("com.miui.msa.global", PUBLIC_DATA, "com.miui.msa.global/filespush_ad_preload/$rngString")
        pos("com.miui.msa.global", PUBLIC_DATA, "com.miui.msa.global/filessplash_preload/$rngString")
        confirm(create())
    }

    @Test fun `vast rtb ad caches`() = runTest {
        addDefaultNegatives()
        neg("com.some.pkg", PUBLIC_DATA, "com.some.pkg/files/vast_rtb_cache")
        pos("com.some.pkg", PUBLIC_DATA, "com.some.pkg/files/vast_rtb_cache/$rngString")
        confirm(create())
    }

    @Test fun `dont match default folder`() = runTest {
        neg("com.some.pkg", PUBLIC_DATA, "com.some.pkg/cache/vast_rtb_cache/$rngString")
        neg("com.some.pkg", PUBLIC_DATA, "com.some.pkg/Cache/vast_rtb_cache/$rngString")

        neg("com.some.pkg", PRIVATE_DATA, "com.some.pkg/cache/vast_rtb_cache/$rngString")
        neg("com.some.pkg", PRIVATE_DATA, "com.some.pkg/Cache/vast_rtb_cache/$rngString")

        confirm(create())
    }

    @Test fun `IFlyAdImgCache ads`() = runTest {
        addDefaultNegatives()

        neg("com.zhihu.android", PRIVATE_DATA, "com.zhihu.android/files/IFlyAdImgCache")
        pos("com.zhihu.android", PRIVATE_DATA, "com.zhihu.android/files/IFlyAdImgCache/$rngString")

        neg("com.zhihu.android", PUBLIC_DATA, "com.zhihu.android/files/IFlyAdImgCache")
        pos("com.zhihu.android", PUBLIC_DATA, "com.zhihu.android/files/IFlyAdImgCache/$rngString")

        confirm(create())
    }

    @Test fun `SHAREit ads`() = runTest {
        val pkgs = setOf(
            "com.lenovo.anyshare.gps",
            "com.lenovo.anyshare",
            "shareit.lite",
            "shareit.premium",
        )
        pkgs.forEach { pkg ->
            neg(pkg, PUBLIC_DATA, "$pkg/files/cooperation")
            pos(pkg, PUBLIC_DATA, "$pkg/files/cooperation/anything")
            neg(pkg, PUBLIC_DATA, "$pkg/files/.ad")
            pos(pkg, PUBLIC_DATA, "$pkg/files/.ad/anything")
            neg(pkg, PUBLIC_DATA, "$pkg/files/mb")
            pos(pkg, PUBLIC_DATA, "$pkg/files/mb/anything")
        }
        confirm(create())
    }
}