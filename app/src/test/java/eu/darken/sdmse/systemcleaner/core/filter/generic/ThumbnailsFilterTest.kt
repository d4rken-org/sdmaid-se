package eu.darken.sdmse.systemcleaner.core.filter.generic

import eu.darken.sdmse.common.areas.DataArea.Type
import eu.darken.sdmse.common.randomString
import eu.darken.sdmse.systemcleaner.core.BaseSieve
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilterTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ThumbnailsFilterTest : SystemCleanerFilterTest() {

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = ThumbnailsFilter(
        baseSieveFactory = object : BaseSieve.Factory {
            override fun create(config: BaseSieve.Config): BaseSieve = BaseSieve(config, fileForensics)
        },
        areaManager = areaManager,
    )

    @Test fun testFilter() = runTest {
        mockDefaults()
        val rndParent = randomString()
        mockNegative(Type.SDCARD, ".thumbnails", Flags.DIR)
        mockNegative(Type.SDCARD, ".thumbnails", Flags.FILE)
        mockNegative(Type.SDCARD, "DCIM", Flags.FILE)
        mockNegative(Type.SDCARD, "DCIM", Flags.DIR)
        mockNegative(Type.SDCARD, "DCIM/.thumbnails", Flags.DIR)
        mockNegative(Type.SDCARD, "DCIM/.thumbnails", Flags.FILE)
        mockNegative(Type.SDCARD, rndParent, Flags.DIR)
        mockNegative(Type.SDCARD, "$rndParent/.thumbnails", Flags.DIR)
        mockNegative(Type.SDCARD, "DCIM", Flags.DIR)
        mockNegative(Type.SDCARD, "DCIM/.thumbnails", Flags.DIR)
        mockNegative(Type.SDCARD, "DCIM/Camera/.thumbnails", Flags.DIR)
        mockNegative(Type.SDCARD, "DCIM/Camera/thumbnails", Flags.DIR)
        mockPositive(Type.SDCARD, "DCIM/.thumbnails/${randomString()}", Flags.FILE)
        mockPositive(Type.SDCARD, "DCIM/.thumbnails/.database_uuid", Flags.FILE)
        mockPositive(Type.SDCARD, "$rndParent/.thumbnails/${randomString()}", Flags.FILE)
        mockPositive(Type.SDCARD, ".thumbnails/G900FXXU1POEA_5.0/movie_22170", Flags.DIR)
        mockPositive(Type.SDCARD, ".thumbnails/movie_22170", Flags.DIR)
        mockPositive(Type.SDCARD, ".thumbnails/.database_uuid", Flags.FILE)
        mockPositive(Type.SDCARD, "DCIM/.thumbnails/${randomString()}", Flags.FILE)
        mockPositive(Type.SDCARD, "DCIM/.thumbnails/.thumbdata${randomString()}", Flags.FILE)
        mockPositive(Type.SDCARD, "DCIM/.thumbnails/asdkasdk123123kasd.jpg", Flags.FILE)
        mockPositive(Type.SDCARD, "DCIM/.thumbnails/asdkasdk123123kasd.jpeg", Flags.FILE)
        mockPositive(Type.SDCARD, "DCIM/Camera/.thumbnails/fileasdkasdk123123kasd!%)(&.jpeg", Flags.FILE)
        mockPositive(Type.SDCARD, "DCIM/Camera/.thumbnails/dirasdkasdk123123kasd!%)(&.jpeg", Flags.DIR)
        mockPositive(Type.SDCARD, "DCIM/Camera/thumbnails/fileasdkasdk123123kasd!%)(&.jpeg", Flags.FILE)
        mockPositive(Type.SDCARD, "DCIM/Camera/thumbnails/dirasdkasdk123123kasd!%)(&.jpeg", Flags.DIR)
        confirm(create())
    }
}
