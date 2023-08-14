package eu.darken.sdmse.systemcleaner.core.filter.stock

import eu.darken.sdmse.common.areas.DataArea.Type.SDCARD
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilterTest
import eu.darken.sdmse.systemcleaner.core.sieve.BaseSieve
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AdvertisementFilterTest : SystemCleanerFilterTest() {

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = AdvertisementFilter(
        baseSieveFactory = object : BaseSieve.Factory {
            override fun create(config: BaseSieve.Config): BaseSieve = BaseSieve(config, fileForensics)
        },
        areaManager = areaManager,
    )

    @Test fun testFilter() = runTest {
        mockDefaults()
        neg(SDCARD, ".ppy_cross", Flag.File)
        neg(SDCARD, "ppy_crossX", Flag.File)
        pos(SDCARD, "ppy_cross", Flag.File)
        neg(SDCARD, ".mologiqX", Flag.Dir)
        pos(SDCARD, ".mologiq", Flag.Dir)
        pos(SDCARD, ".mologiq/file!", Flag.File)
        neg(SDCARD, ".AdcenixX", Flag.Dir)
        pos(SDCARD, ".Adcenix", Flag.Dir)
        pos(SDCARD, ".Adcenix/file!", Flag.File)
        neg(SDCARD, "ApplifierImageCacheX", Flag.Dir)
        pos(SDCARD, "ApplifierImageCache", Flag.Dir)
        pos(SDCARD, "ApplifierImageCache/file!", Flag.File)
        neg(SDCARD, "burstlyImageCacheX", Flag.Dir)
        pos(SDCARD, "burstlyImageCache", Flag.Dir)
        pos(SDCARD, "burstlyImageCache/file!", Flag.File)
        neg(SDCARD, "UnityAdsImageCacheX", Flag.Dir)
        pos(SDCARD, "UnityAdsImageCache", Flag.Dir)
        pos(SDCARD, "UnityAdsImageCache/file!", Flag.File)
        neg(SDCARD, "ApplifierVideoCacheX", Flag.Dir)
        pos(SDCARD, "ApplifierVideoCache", Flag.Dir)
        pos(SDCARD, "ApplifierVideoCache/file!", Flag.File)
        neg(SDCARD, "burstlyVideoCacheX", Flag.Dir)
        pos(SDCARD, "burstlyVideoCache", Flag.Dir)
        pos(SDCARD, "burstlyVideoCache/file!", Flag.File)
        neg(SDCARD, "UnityAdsVideoCacheX", Flag.Dir)
        pos(SDCARD, "UnityAdsVideoCache", Flag.Dir)
        pos(SDCARD, "UnityAdsVideoCache/file!", Flag.File)
        neg(SDCARD, "_chartboost", Flag.Dir)
        neg(SDCARD, "chartboost", Flag.Dir)
        neg(SDCARD, "__chartboostX", Flag.Dir)
        neg(SDCARD, "__chartboost", Flag.File)
        pos(SDCARD, "__chartboost", Flag.Dir)
        pos(SDCARD, "__chartboost/file!", Flag.File)
        neg(SDCARD, ".chartboostX", Flag.Dir)
        neg(SDCARD, ".chartboost", Flag.File)
        pos(SDCARD, ".chartboost", Flag.Dir)
        pos(SDCARD, ".chartboost/file!", Flag.File)
        neg(SDCARD, "adhubX", Flag.Dir)
        pos(SDCARD, "adhub", Flag.Dir)
        pos(SDCARD, "adhub/adhubk.db", Flag.File)
        neg(SDCARD, ".mobvista", Flag.File)
        neg(SDCARD, ".mobvista", Flag.Dir)
        pos(SDCARD, ".mobvista700", Flag.File)
        pos(SDCARD, ".mobvista700", Flag.Dir)
        pos(SDCARD, ".mobvista800", Flag.Dir)
        pos(SDCARD, ".mobvista800/something", Flag.Dir)
        pos(SDCARD, ".goadsdk", Flag.Dir)
        pos(SDCARD, ".goadsdk/something", Flag.Dir)
        pos(SDCARD, ".goproduct", Flag.Dir)
        pos(SDCARD, ".goproduct/something", Flag.Dir)
        confirm(create())
    }

}