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

class RecentTasksFilterTest : SystemCleanerFilterTest() {

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = RecentTasksFilter(
        baseSieveFactory = object : BaseSieve.Factory {
            override fun create(config: BaseSieve.Config): BaseSieve = BaseSieve(config, fileForensics)
        },
        areaManager = areaManager,
    )

    @Test fun testFilter() = runTest {
        mockDefaults()

        mockNegative(Type.DATA_SYSTEM_CE, "testdir", Flags.DIR)
        mockNegative(Type.DATA_SYSTEM_CE, "testfile", Flags.FILE)
        mockNegative(Type.DATA_SYSTEM_CE, "recent_tasks", Flags.DIR)
        mockPositive(Type.DATA_SYSTEM_CE, "recent_tasks/test", Flags.FILE)
        mockNegative(Type.DATA_SYSTEM_CE, "recent_images", Flags.DIR)
        mockPositive(Type.DATA_SYSTEM_CE, "recent_images/test", Flags.FILE)

        confirm(create())
    }

    @Test fun `only with root`() = runTest {
        RecentTasksFilter.Factory(
            settings = mockk<SystemCleanerSettings>().apply {
                coEvery { filterRecentTasksEnabled } returns mockDataStoreValue(true)
            },
            filterProvider = mockk(),
            rootManager = mockk<RootManager>().apply {
                every { useRoot } returns flowOf(true)
            }
        ).isEnabled() shouldBe true

        RecentTasksFilter.Factory(
            settings = mockk<SystemCleanerSettings>().apply {
                coEvery { filterRecentTasksEnabled } returns mockDataStoreValue(true)
            },
            filterProvider = mockk(),
            rootManager = mockk<RootManager>().apply {
                every { useRoot } returns flowOf(false)
            }
        ).isEnabled() shouldBe false
    }
}