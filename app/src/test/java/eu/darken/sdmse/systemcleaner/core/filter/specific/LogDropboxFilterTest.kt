package eu.darken.sdmse.systemcleaner.core.filter.specific

import eu.darken.sdmse.common.areas.DataArea.Type
import eu.darken.sdmse.common.rngString
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

        mockNegative(Type.DATA, "system", Flags.DIR)
        mockNegative(Type.DATA_SYSTEM, "", Flags.DIR)
        mockNegative(Type.DATA_SYSTEM, "dropbox", Flags.DIR)
        mockNegative(Type.DATA_SYSTEM, "dropbox/$rngString", Flags.DIR)
        mockNegative(Type.DATA_SYSTEM, "dropbox/$rngString", Flags.DIR)
        mockNegative(Type.DATA, "system/dropbox/$rngString", Flags.DIR)
        mockNegative(Type.DATA, "dropbox/event_data@1483828487660.txt", Flags.FILE)
        mockNegative(Type.DATA_SYSTEM_CE, "dropbox/$rngString", Flags.FILE)
        mockNegative(Type.DATA_SYSTEM_DE, "dropbox/$rngString", Flags.FILE)
        mockPositive(Type.DATA_SYSTEM, "dropbox/$rngString", Flags.FILE)
        mockPositive(Type.DATA_SYSTEM, "dropbox/$rngString/something", Flags.FILE)
        mockPositive(Type.DATA_SYSTEM, "dropbox/event_data@1483828487660.txt", Flags.FILE)
        mockPositive(Type.DATA_SYSTEM, "dropbox/platform_stats_bookmark@1483690326366.txt", Flags.FILE)

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