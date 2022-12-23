package eu.darken.sdmse.systemcleaner.core.filter.generic

import eu.darken.sdmse.common.areas.DataArea.Type
import eu.darken.sdmse.systemcleaner.core.BaseSieve
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilterTest
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
        mockNegative(Type.SDCARD, ".ppy_cross", Flags.FILE)
        mockNegative(Type.SDCARD, "ppy_crossX", Flags.FILE)
        mockPositive(Type.SDCARD, "ppy_cross", Flags.FILE)
        mockNegative(Type.SDCARD, ".mologiqX", Flags.DIR)
        mockPositive(Type.SDCARD, ".mologiq", Flags.DIR)
        mockPositive(Type.SDCARD, ".mologiq/file!", Flags.FILE)
        mockNegative(Type.SDCARD, ".AdcenixX", Flags.DIR)
        mockPositive(Type.SDCARD, ".Adcenix", Flags.DIR)
        mockPositive(Type.SDCARD, ".Adcenix/file!", Flags.FILE)
        mockNegative(Type.SDCARD, "ApplifierImageCacheX", Flags.DIR)
        mockPositive(Type.SDCARD, "ApplifierImageCache", Flags.DIR)
        mockPositive(Type.SDCARD, "ApplifierImageCache/file!", Flags.FILE)
        mockNegative(Type.SDCARD, "burstlyImageCacheX", Flags.DIR)
        mockPositive(Type.SDCARD, "burstlyImageCache", Flags.DIR)
        mockPositive(Type.SDCARD, "burstlyImageCache/file!", Flags.FILE)
        mockNegative(Type.SDCARD, "UnityAdsImageCacheX", Flags.DIR)
        mockPositive(Type.SDCARD, "UnityAdsImageCache", Flags.DIR)
        mockPositive(Type.SDCARD, "UnityAdsImageCache/file!", Flags.FILE)
        mockNegative(Type.SDCARD, "ApplifierVideoCacheX", Flags.DIR)
        mockPositive(Type.SDCARD, "ApplifierVideoCache", Flags.DIR)
        mockPositive(Type.SDCARD, "ApplifierVideoCache/file!", Flags.FILE)
        mockNegative(Type.SDCARD, "burstlyVideoCacheX", Flags.DIR)
        mockPositive(Type.SDCARD, "burstlyVideoCache", Flags.DIR)
        mockPositive(Type.SDCARD, "burstlyVideoCache/file!", Flags.FILE)
        mockNegative(Type.SDCARD, "UnityAdsVideoCacheX", Flags.DIR)
        mockPositive(Type.SDCARD, "UnityAdsVideoCache", Flags.DIR)
        mockPositive(Type.SDCARD, "UnityAdsVideoCache/file!", Flags.FILE)
        mockNegative(Type.SDCARD, "_chartboost", Flags.DIR)
        mockNegative(Type.SDCARD, "chartboost", Flags.DIR)
        mockNegative(Type.SDCARD, "__chartboostX", Flags.DIR)
        mockNegative(Type.SDCARD, "__chartboost", Flags.FILE)
        mockPositive(Type.SDCARD, "__chartboost", Flags.DIR)
        mockPositive(Type.SDCARD, "__chartboost/file!", Flags.FILE)
        mockNegative(Type.SDCARD, ".chartboostX", Flags.DIR)
        mockNegative(Type.SDCARD, ".chartboost", Flags.FILE)
        mockPositive(Type.SDCARD, ".chartboost", Flags.DIR)
        mockPositive(Type.SDCARD, ".chartboost/file!", Flags.FILE)
        mockNegative(Type.SDCARD, "adhubX", Flags.DIR)
        mockPositive(Type.SDCARD, "adhub", Flags.DIR)
        mockPositive(Type.SDCARD, "adhub/adhubk.db", Flags.FILE)
        mockNegative(Type.SDCARD, ".mobvista", Flags.FILE)
        mockNegative(Type.SDCARD, ".mobvista", Flags.DIR)
        mockPositive(Type.SDCARD, ".mobvista700", Flags.FILE)
        mockPositive(Type.SDCARD, ".mobvista700", Flags.DIR)
        mockPositive(Type.SDCARD, ".mobvista800", Flags.DIR)
        mockPositive(Type.SDCARD, ".mobvista800/something", Flags.DIR)
        confirm(create())
    }

}