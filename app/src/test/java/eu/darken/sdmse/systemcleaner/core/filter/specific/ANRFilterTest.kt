package eu.darken.sdmse.systemcleaner.core.filter.specific

import eu.darken.sdmse.common.areas.DataArea.Type
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.systemcleaner.core.BaseSieve
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilterTest
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

class ANRFilterTest : SystemCleanerFilterTest() {

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = AnrFilter(
        baseSieveFactory = object : BaseSieve.Factory {
            override fun create(config: BaseSieve.Config): BaseSieve = BaseSieve(config, fileForensics)
        },
        areaManager = areaManager,
    )

    @Test fun testFilter() = runTest {
        mockDefaults()
        mockNegative(Type.DATA, "anr", Flags.DIR)
        mockNegative(Type.DATA, "anr/something.txt", Flags.FILE, Flags.ONLY_SECONDARY)
        mockNegative(Type.DATA, "anr/something.bugreports", Flags.FILE, Flags.ONLY_SECONDARY)
        mockPositive(Type.DATA, "anr/something.txt", Flags.FILE, Flags.ONLY_PRIMARY)
        mockPositive(Type.DATA, "anr/something.bugreports", Flags.FILE, Flags.ONLY_PRIMARY)
        mockPositive(Type.DATA, "anr/trace_00", Flags.FILE, Flags.ONLY_PRIMARY)
        mockPositive(Type.DATA, "anr/anr_2021-11-05-07-26-10-770", Flags.FILE, Flags.ONLY_PRIMARY)
        mockPositive(Type.DATA, "anr/anr_2021-11-05-12-43-35-418", Flags.FILE, Flags.ONLY_PRIMARY)
        confirm(create())
    }

    @Test fun `only with root`() = runTest {
        AnrFilter.Factory(
            settings = mockk<SystemCleanerSettings>().apply {
                coEvery { filterAnrEnabled } returns mockDataStoreValue(true)
            },
            filterProvider = mockk(),
            rootManager = mockk<RootManager>().apply {
                every { useRoot } returns flowOf(true)
            }
        ).isEnabled() shouldBe true

        AnrFilter.Factory(
            settings = mockk<SystemCleanerSettings>().apply {
                coEvery { filterAnrEnabled } returns mockDataStoreValue(true)
            },
            filterProvider = mockk(),
            rootManager = mockk<RootManager>().apply {
                every { useRoot } returns flowOf(false)
            }
        ).isEnabled() shouldBe false
    }
}