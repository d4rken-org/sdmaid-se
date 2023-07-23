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

class DataLoggerFilterTest : SystemCleanerFilterTest() {

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = DataLoggerFilter(
        baseSieveFactory = object : BaseSieve.Factory {
            override fun create(config: BaseSieve.Config): BaseSieve = BaseSieve(config, fileForensics)
        },
        areaManager = areaManager,
    )

    @Test fun testFilter() = runTest {
        mockDefaults()
        mockNegative(Type.DATA, "logger", Flags.DIR)
        mockNegative(Type.DATA, "logger/adir", Flags.DIR)
        mockNegative(Type.DATA, "logger/setup", Flags.DIR)
        mockNegative(Type.DATA, "logger/setup/adir", Flags.DIR)

        mockPositive(Type.DATA, "logger/something_thing", Flags.FILE)
        mockPositive(Type.DATA, "logger/mode_debug_info", Flags.FILE)
        mockPositive(Type.DATA, "logger/kernel.log", Flags.FILE)
        mockPositive(Type.DATA, "logger/kernel.log.1", Flags.FILE)
        mockPositive(Type.DATA, "logger/last_log", Flags.FILE)
        mockPositive(Type.DATA, "logger/setup/something_thing", Flags.FILE)
        mockPositive(Type.DATA, "logger/setup/something_thing.log", Flags.FILE)

        mockNegative(Type.DATA, "log", Flags.DIR)
        mockNegative(Type.DATA, "log/acore", Flags.DIR)
        mockNegative(Type.DATA, "log/batterystats", Flags.DIR)
        mockNegative(Type.DATA, "log/bt", Flags.DIR)
        mockNegative(Type.DATA, "log/core", Flags.DIR)
        mockNegative(Type.DATA, "log/err", Flags.DIR)
        mockNegative(Type.DATA, "log/ewlogd", Flags.DIR)
        mockNegative(Type.DATA, "log/imscr", Flags.DIR)
        mockNegative(Type.DATA, "log/omc", Flags.DIR)
        mockNegative(Type.DATA, "log/sepunion", Flags.DIR)
        mockNegative(Type.DATA, "log/wifi", Flags.DIR)

        mockPositive(Type.DATA, "log/0_com.samsung.android.bixby.service_bixbysearch_index.log", Flags.FILE)
        mockPositive(Type.DATA, "log/dark_mode_log0.txt", Flags.FILE)
        mockPositive(Type.DATA, "log/power_off_reset_reason.txt", Flags.FILE)
        mockPositive(Type.DATA, "log/$rngString", Flags.FILE)

        mockNegative(Type.DATA, "log_other_mode", Flags.DIR)
        mockNegative(Type.DATA, "log_other_mode/$rngString", Flags.DIR)
        mockPositive(Type.DATA, "log_other_mode/$rngString", Flags.FILE)
        mockNegative(Type.DATA, "log_other_mode/subfolder", Flags.DIR)
        mockPositive(Type.DATA, "log_other_mode/subfolder/$rngString", Flags.FILE)

        confirm(create())
    }

    @Test fun `only with root`() = runTest {
        DataLoggerFilter.Factory(
            settings = mockk<SystemCleanerSettings>().apply {
                coEvery { filterDataLoggerEnabled } returns mockDataStoreValue(true)
            },
            filterProvider = mockk(),
            rootManager = mockk<RootManager>().apply {
                every { useRoot } returns flowOf(true)
            }
        ).isEnabled() shouldBe true

        DataLoggerFilter.Factory(
            settings = mockk<SystemCleanerSettings>().apply {
                coEvery { filterDataLoggerEnabled } returns mockDataStoreValue(true)
            },
            filterProvider = mockk(),
            rootManager = mockk<RootManager>().apply {
                every { useRoot } returns flowOf(false)
            }
        ).isEnabled() shouldBe false
    }
}