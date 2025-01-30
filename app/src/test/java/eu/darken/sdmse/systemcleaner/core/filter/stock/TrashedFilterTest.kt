package eu.darken.sdmse.systemcleaner.core.filter.stock

import eu.darken.sdmse.common.areas.DataArea.Type.PORTABLE
import eu.darken.sdmse.common.areas.DataArea.Type.SDCARD
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilterTest
import eu.darken.sdmse.systemcleaner.core.sieve.BaseSieve
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TrashedFilterTest : SystemCleanerFilterTest() {

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = TrashedFilter(
        baseSieveFactory = object : BaseSieve.Factory {
            override fun create(config: BaseSieve.Config): BaseSieve = BaseSieve(config, fileForensics)
        },
        gatewaySwitch = gatewaySwitch,
    )

    @Test fun testFilter() = runTest {
        mockDefaults()

        neg(SDCARD, "Pictures/1740850032-PXL_20250130_172627042.jpg", Flag.File)
        pos(SDCARD, "Pictures/.trashed-1740850032-PXL_20250130_172627042.jpg", Flag.File)
        neg(SDCARD, "DCIM/Camera/.trashed-1740849860-PXL_20241015_101215095.TS.mp4", Flag.Dir)
        neg(SDCARD, "DCIM/Camera/1740849860-PXL_20241015_101215095.TS.mp4", Flag.File)
        pos(SDCARD, "DCIM/Camera/.trashed-1740849860-PXL_20241015_101215095.TS.mp4", Flag.File)

        neg(PORTABLE, "Pictures/1740850032-PXL_20250130_172627042.jpg", Flag.File)
        pos(PORTABLE, "Pictures/.trashed-1740850032-PXL_20250130_172627042.jpg", Flag.File)
        neg(PORTABLE, "DCIM/Camera/.trashed-1740849860-PXL_20241015_101215095.TS.mp4", Flag.Dir)
        neg(PORTABLE, "DCIM/Camera/1740849860-PXL_20241015_101215095.TS.mp4", Flag.File)
        pos(PORTABLE, "DCIM/Camera/.trashed-1740849860-PXL_20241015_101215095.TS.mp4", Flag.File)

        confirm(create())
    }
}
