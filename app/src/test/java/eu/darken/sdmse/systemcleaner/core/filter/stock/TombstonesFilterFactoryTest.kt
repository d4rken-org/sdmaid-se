package eu.darken.sdmse.systemcleaner.core.filter.stock

import eu.darken.sdmse.common.areas.DataArea.Type
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilterTest
import eu.darken.sdmse.systemcleaner.core.sieve.SystemCrawlerSieve
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.mockDataStoreValue

class TombstonesFilterFactoryTest : SystemCleanerFilterTest() {

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = TombstonesFilter(
        sieveFactory = object : SystemCrawlerSieve.Factory {
            override fun create(config: SystemCrawlerSieve.Config): SystemCrawlerSieve =
                SystemCrawlerSieve(config, fileForensics)
        },
        gatewaySwitch = gatewaySwitch,
    )

    @Test fun `only with root`() = runTest {
        TombstonesFilter.Factory(
            settings = mockk<SystemCleanerSettings>().apply {
                coEvery { filterTombstonesEnabled } returns mockDataStoreValue(true)
            },
            filterProvider = mockk(),
            rootManager = mockk<RootManager>().apply {
                every { useRoot } returns flowOf(true)
            }
        ).isEnabled() shouldBe true

        TombstonesFilter.Factory(
            settings = mockk<SystemCleanerSettings>().apply {
                coEvery { filterTombstonesEnabled } returns mockDataStoreValue(true)
            },
            filterProvider = mockk(),
            rootManager = mockk<RootManager>().apply {
                every { useRoot } returns flowOf(false)
            }
        ).isEnabled() shouldBe false
    }

    @Test fun testFilter() = runTest {
        mockDefaults()

        neg(Type.DATA, "tombstones", Flag.Dir)
        neg(Type.DATA, "tombstones", Flag.File)
        pos(Type.DATA, "tombstones/$rngString", Flag.File)

        confirm(create())
    }

    @Test fun `vendor tombstones`() = runTest {
        mockDefaults()

        neg(Type.DATA_VENDOR, "tombstones", Flag.Dir)
        neg(Type.DATA_VENDOR, "tombstones/wifi", Flag.Dir)
        pos(Type.DATA_VENDOR, "tombstones/wifi/driver_logEMKh3RgU3T", Flag.File)
        pos(Type.DATA_VENDOR, "tombstones/wifi/driver_logkV2SGCjtIq", Flag.File)
        pos(Type.DATA_VENDOR, "tombstones/wifi/ecntrs_fSXArAwhYY", Flag.File)
        pos(Type.DATA_VENDOR, "tombstones/wifi/fw_verboseK3zRFC3FuW", Flag.File)
        pos(Type.DATA_VENDOR, "tombstones/wifi/fw_verbosePNYNU3FqVL", Flag.File)
        pos(Type.DATA_VENDOR, "tombstones/wifi/packet_log4bdikVnHlp", Flag.File)
        pos(Type.DATA_VENDOR, "tombstones/wifi/roam_statsK7aznO0wRR", Flag.File)
        neg(Type.DATA_VENDOR, "tombstones/something", Flag.Dir)
        pos(Type.DATA_VENDOR, "tombstones/something/qwerzty_log4bdiasdHlp", Flag.File)

        confirm(create())
    }
}
