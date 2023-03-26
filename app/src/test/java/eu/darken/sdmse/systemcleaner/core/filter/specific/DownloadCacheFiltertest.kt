package eu.darken.sdmse.systemcleaner.core.filter.specific

import eu.darken.sdmse.common.areas.DataArea
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

class DownloadCacheFiltertest : SystemCleanerFilterTest() {

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = DownloadCacheFilter(
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
        mockNegative(DataArea.Type.DOWNLOAD_CACHE, "recovery/last_postrecovery", Flags.FILE)
        mockNegative(DataArea.Type.DOWNLOAD_CACHE, "recovery/last_data_partition_info", Flags.FILE)
        mockNegative(DataArea.Type.DOWNLOAD_CACHE, "recovery/last_dataresizing", Flags.FILE)
        mockNegative(DataArea.Type.DOWNLOAD_CACHE, rngString, Flags.DIR)
        mockPositive(DataArea.Type.DOWNLOAD_CACHE, rngString, Flags.FILE)
        mockPositive(DataArea.Type.DOWNLOAD_CACHE, "recovery/$rngString", Flags.FILE)
        mockPositive(DataArea.Type.DOWNLOAD_CACHE, "magisk.log", Flags.FILE)
        mockPositive(DataArea.Type.DOWNLOAD_CACHE, "magisk.log.bak", Flags.FILE)
        confirm(create())
    }

    @Test fun `only with root`() = runTest {
        DownloadCacheFilter.Factory(
            settings = mockk<SystemCleanerSettings>().apply {
                coEvery { filterDownloadCacheEnabled } returns mockDataStoreValue(true)
            },
            filterProvider = mockk(),
            rootManager = mockk<RootManager>().apply {
                coEvery { useRoot() } returns true
            }
        ).isEnabled() shouldBe true

        DownloadCacheFilter.Factory(
            settings = mockk<SystemCleanerSettings>().apply {
                coEvery { filterDownloadCacheEnabled } returns mockDataStoreValue(true)
            },
            filterProvider = mockk(),
            rootManager = mockk<RootManager>().apply {
                coEvery { useRoot() } returns false
            }
        ).isEnabled() shouldBe false
    }
}
