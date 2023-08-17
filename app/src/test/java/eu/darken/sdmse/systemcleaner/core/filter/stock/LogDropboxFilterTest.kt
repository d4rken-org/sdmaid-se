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

class LogDropboxFilterTest : SystemCleanerFilterTest() {

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = LogDropboxFilter(
        baseSieveFactory = object : BaseSieve.Factory {
            override fun create(config: BaseSieve.Config): BaseSieve = BaseSieve(config, fileForensics)
        },
        areaManager = areaManager,
    )

    @Test fun testFilter() = runTest {
        mockDefaults()

        neg(Type.DATA, "dropbox", Flag.Dir)
        neg(Type.DATA, "dropbox", Flag.File)
        neg(Type.DATA, "dropbox/event_data@1483828487669.txt", Flag.File)

        neg(Type.DATA, "system/dropbox", Flag.Dir)
        neg(Type.DATA, "system/dropbox/$rngString", Flag.Dir)
        neg(Type.DATA_SYSTEM, "dropbox/$rngString", Flag.Dir)

        neg(Type.DATA_SYSTEM_CE, "dropbox", Flag.Dir)
        neg(Type.DATA_SYSTEM_CE, "dropbox/$rngString", Flag.File)

        neg(Type.DATA_SYSTEM_DE, "dropbox", Flag.Dir)
        neg(Type.DATA_SYSTEM_DE, "dropbox/$rngString", Flag.File)

        pos(Type.DATA_SYSTEM, "dropbox/$rngString", Flag.File)
        val someDir = rngString
        neg(Type.DATA_SYSTEM, "dropbox/$someDir", Flag.Dir)
        pos(Type.DATA_SYSTEM, "dropbox/$someDir/something", Flag.File)
        pos(Type.DATA_SYSTEM, "dropbox/event_data@1483828487660.txt", Flag.File)
        pos(Type.DATA_SYSTEM, "dropbox/platform_stats_bookmark@1483690326366.txt", Flag.File)

        confirm(create())
    }

    @Test fun `only with root`() = runTest {
        LogDropboxFilter.Factory(
            settings = mockk<SystemCleanerSettings>().apply {
                coEvery { filterLogDropboxEnabled } returns mockDataStoreValue(true)
            },
            filterProvider = mockk(),
            rootManager = mockk<RootManager>().apply {
                every { useRoot } returns flowOf(true)
            }
        ).isEnabled() shouldBe true

        LogDropboxFilter.Factory(
            settings = mockk<SystemCleanerSettings>().apply {
                coEvery { filterLogDropboxEnabled } returns mockDataStoreValue(true)
            },
            filterProvider = mockk(),
            rootManager = mockk<RootManager>().apply {
                every { useRoot } returns flowOf(false)
            }
        ).isEnabled() shouldBe false
    }
}