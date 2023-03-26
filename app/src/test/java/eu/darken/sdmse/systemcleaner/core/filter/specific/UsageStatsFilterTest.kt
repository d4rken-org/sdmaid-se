package eu.darken.sdmse.systemcleaner.core.filter.specific

import eu.darken.sdmse.common.areas.DataArea.Type
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.systemcleaner.core.BaseSieve
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilterTest
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.mockDataStoreValue

class UsageStatsFilterTest : SystemCleanerFilterTest() {

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = UsagestatsFilter(
        baseSieveFactory = object : BaseSieve.Factory {
            override fun create(config: BaseSieve.Config): BaseSieve = BaseSieve(config, fileForensics)
        },
        areaManager = areaManager,
    )

    @Test fun testFilter() = runTest {
        mockDefaults()

        mockNegative(Type.DATA_SYSTEM, "usagestats/0/daily", Flags.DIR)
        mockNegative(Type.DATA_SYSTEM, "usagestats/0/daily/$rngString", Flags.DIR)
        mockPositive(Type.DATA_SYSTEM, "usagestats/0/daily/$rngString", Flags.FILE)
        mockNegative(Type.DATA_SYSTEM, "usagestats/13/daily", Flags.DIR)
        mockPositive(Type.DATA_SYSTEM, "usagestats/13/daily/$rngString", Flags.FILE)
        mockNegative(Type.DATA_SYSTEM, "usagestats/0/weekly", Flags.DIR)
        mockPositive(Type.DATA_SYSTEM, "usagestats/0/weekly/$rngString", Flags.FILE)
        mockNegative(Type.DATA_SYSTEM, "usagestats/13/weekly", Flags.DIR)
        mockPositive(Type.DATA_SYSTEM, "usagestats/13/weekly/$rngString", Flags.FILE)
        mockNegative(Type.DATA_SYSTEM, "usagestats/0/yearly", Flags.DIR)
        mockPositive(Type.DATA_SYSTEM, "usagestats/0/yearly/$rngString", Flags.FILE)
        mockNegative(Type.DATA_SYSTEM, "usagestats/13/yearly", Flags.DIR)
        mockPositive(Type.DATA_SYSTEM, "usagestats/13/yearly/$rngString", Flags.FILE)
        mockNegative(Type.DATA_SYSTEM, "usagestats/usage-$rngString", Flags.FILE)

        confirm(create())
    }

    @Test fun `only with root`() = runTest {
        UsagestatsFilter.Factory(
            settings = mockk<SystemCleanerSettings>().apply {
                coEvery { filterUsageStatsEnabled } returns mockDataStoreValue(true)
            },
            filterProvider = mockk(),
            rootManager = mockk<RootManager>().apply {
                coEvery { useRoot() } returns true
            }
        ).isEnabled() shouldBe true

        UsagestatsFilter.Factory(
            settings = mockk<SystemCleanerSettings>().apply {
                coEvery { filterUsageStatsEnabled } returns mockDataStoreValue(true)
            },
            filterProvider = mockk(),
            rootManager = mockk<RootManager>().apply {
                coEvery { useRoot() } returns false
            }
        ).isEnabled() shouldBe false
    }
}
