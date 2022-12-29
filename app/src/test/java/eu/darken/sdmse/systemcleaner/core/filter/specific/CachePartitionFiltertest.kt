package eu.darken.sdmse.systemcleaner.core.filter.specific

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.randomString
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

class CachePartitionFiltertest : SystemCleanerFilterTest() {

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = CachePartitionFilter(
        baseSieveFactory = object : BaseSieve.Factory {
            override fun create(config: BaseSieve.Config): BaseSieve = BaseSieve(config, fileForensics)
        },
        areaManager = areaManager,
    )

    @Test fun testFilter() = runTest {
        mockDefaults()
        mockNegative(DataArea.Type.DOWNLOAD_CACHE, "dalvik-cache", Flags.DIR)
        mockNegative(DataArea.Type.DOWNLOAD_CACHE, "lost+found", Flags.DIR)
        mockNegative(DataArea.Type.DOWNLOAD_CACHE, "recovery/last_log", Flags.DIR)
        mockNegative(DataArea.Type.DOWNLOAD_CACHE, randomString(), Flags.DIR)
        mockPositive(DataArea.Type.DOWNLOAD_CACHE, randomString(), Flags.FILE)
        mockPositive(DataArea.Type.DOWNLOAD_CACHE, "recovery/${randomString()}", Flags.FILE)
        confirm(create())
    }

    @Test fun `only with root`() = runTest {
        CachePartitionFilter.Factory(
            settings = mockk<SystemCleanerSettings>().apply {
                coEvery { filterCachePartitionEnabled } returns mockDataStoreValue(true)
            },
            filterProvider = mockk(),
            rootManager = mockk<RootManager>().apply {
                coEvery { isRooted() } returns true
            }
        ).isEnabled() shouldBe true

        CachePartitionFilter.Factory(
            settings = mockk<SystemCleanerSettings>().apply {
                coEvery { filterCachePartitionEnabled } returns mockDataStoreValue(true)
            },
            filterProvider = mockk(),
            rootManager = mockk<RootManager>().apply {
                coEvery { isRooted() } returns false
            }
        ).isEnabled() shouldBe false
    }
}
