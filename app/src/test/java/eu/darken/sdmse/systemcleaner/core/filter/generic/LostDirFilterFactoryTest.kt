package eu.darken.sdmse.systemcleaner.core.filter.generic

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.randomString
import eu.darken.sdmse.systemcleaner.core.BaseSieve
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilterTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LostDirFilterFactoryTest : SystemCleanerFilterTest() {

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = LostDirFilter(
        baseSieveFactory = object : BaseSieve.Factory {
            override fun create(config: BaseSieve.Config): BaseSieve = BaseSieve(config, fileForensics)
        },
        areaManager = areaManager,
    )

    @Test fun testFilter() = runTest {
        mockDefaults()
        mockNegative(DataArea.Type.SDCARD, "LOST.DIR", Flags.DIR)
        mockNegative(DataArea.Type.SDCARD, "somedir/LOST.DIR", Flags.DIR)
        mockNegative(DataArea.Type.SDCARD, "LOST.DIR/${randomString()}", Flags.DIR)
        mockPositive(DataArea.Type.SDCARD, "LOST.DIR/${randomString()}", Flags.FILE)
        confirm(create())
    }


}