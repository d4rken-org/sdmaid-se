package eu.darken.sdmse.systemcleaner.core.filter.stock

import eu.darken.sdmse.common.areas.DataArea
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
        neg(DataArea.Type.DOWNLOAD_CACHE, "dalvik-cache", Flag.Dir)
        neg(DataArea.Type.DOWNLOAD_CACHE, "lost+found", Flag.Dir)
        neg(DataArea.Type.DOWNLOAD_CACHE, "recovery", Flag.Dir)
        neg(DataArea.Type.DOWNLOAD_CACHE, "recovery/last_log", Flag.Dir)
        neg(DataArea.Type.DOWNLOAD_CACHE, "recovery/last_postrecovery", Flag.File)
        neg(DataArea.Type.DOWNLOAD_CACHE, "recovery/last_data_partition_info", Flag.File)
        neg(DataArea.Type.DOWNLOAD_CACHE, "recovery/last_dataresizing", Flag.File)
        neg(DataArea.Type.DOWNLOAD_CACHE, rngString, Flag.Dir)
        pos(DataArea.Type.DOWNLOAD_CACHE, rngString, Flag.File)
        pos(DataArea.Type.DOWNLOAD_CACHE, "recovery/$rngString", Flag.File)
        pos(DataArea.Type.DOWNLOAD_CACHE, "magisk.log", Flag.File)
        pos(DataArea.Type.DOWNLOAD_CACHE, "magisk.log.bak", Flag.File)
        confirm(create())
    }

    @Test fun `only with root`() = runTest {
        DownloadCacheFilter.Factory(
            settings = mockk<SystemCleanerSettings>().apply {
                coEvery { filterDownloadCacheEnabled } returns mockDataStoreValue(true)
            },
            filterProvider = mockk(),
            rootManager = mockk<RootManager>().apply {
                every { useRoot } returns flowOf(true)
            }
        ).isEnabled() shouldBe true

        DownloadCacheFilter.Factory(
            settings = mockk<SystemCleanerSettings>().apply {
                coEvery { filterDownloadCacheEnabled } returns mockDataStoreValue(true)
            },
            filterProvider = mockk(),
            rootManager = mockk<RootManager>().apply {
                every { useRoot } returns flowOf(false)
            }
        ).isEnabled() shouldBe false
    }
}
