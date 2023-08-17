package eu.darken.sdmse.systemcleaner.core.filter.stock

import eu.darken.sdmse.common.areas.DataArea.Type
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilterTest
import eu.darken.sdmse.systemcleaner.core.sieve.BaseSieve
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

        neg(Type.DATA_SYSTEM, "usagestats", Flag.Dir)
        neg(Type.DATA_SYSTEM, "usagestats", Flag.File)

        neg(Type.DATA_SYSTEM, "usagestats/0", Flag.Dir)
        neg(Type.DATA_SYSTEM, "usagestats/0/daily", Flag.Dir)
        neg(Type.DATA_SYSTEM, "usagestats/0/daily/$rngString", Flag.Dir)
        pos(Type.DATA_SYSTEM, "usagestats/0/daily/$rngString", Flag.File)
        neg(Type.DATA_SYSTEM, "usagestats/0/weekly", Flag.Dir)
        pos(Type.DATA_SYSTEM, "usagestats/0/weekly/$rngString", Flag.File)
        neg(Type.DATA_SYSTEM, "usagestats/0/yearly", Flag.Dir)
        pos(Type.DATA_SYSTEM, "usagestats/0/yearly/$rngString", Flag.File)

        neg(Type.DATA_SYSTEM, "usagestats/13", Flag.Dir)
        neg(Type.DATA_SYSTEM, "usagestats/13/daily", Flag.Dir)
        pos(Type.DATA_SYSTEM, "usagestats/13/daily/$rngString", Flag.File)
        neg(Type.DATA_SYSTEM, "usagestats/13/weekly", Flag.Dir)
        pos(Type.DATA_SYSTEM, "usagestats/13/weekly/$rngString", Flag.File)
        neg(Type.DATA_SYSTEM, "usagestats/13/yearly", Flag.Dir)
        pos(Type.DATA_SYSTEM, "usagestats/13/yearly/$rngString", Flag.File)
        neg(Type.DATA_SYSTEM, "usagestats/usage-$rngString", Flag.File)

        confirm(create())
    }

    @Test fun `only with root`() = runTest {
        UsagestatsFilter.Factory(
            settings = mockk<SystemCleanerSettings>().apply {
                coEvery { filterUsageStatsEnabled } returns mockDataStoreValue(true)
            },
            filterProvider = mockk(),
            rootManager = mockk<RootManager>().apply {
                every { useRoot } returns flowOf(true)
            }
        ).isEnabled() shouldBe true

        UsagestatsFilter.Factory(
            settings = mockk<SystemCleanerSettings>().apply {
                coEvery { filterUsageStatsEnabled } returns mockDataStoreValue(true)
            },
            filterProvider = mockk(),
            rootManager = mockk<RootManager>().apply {
                every { useRoot } returns flowOf(false)
            }
        ).isEnabled() shouldBe false
    }
}
