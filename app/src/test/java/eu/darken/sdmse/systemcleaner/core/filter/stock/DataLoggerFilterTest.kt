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
        neg(Type.DATA, "logger", Flag.Dir)
        neg(Type.DATA, "logger", Flag.File)
        neg(Type.DATA, "logger/adir", Flag.Dir)
        neg(Type.DATA, "logger/setup", Flag.Dir)
        neg(Type.DATA, "logger/setup/adir", Flag.Dir)

        pos(Type.DATA, "logger/something_thing", Flag.File)
        pos(Type.DATA, "logger/mode_debug_info", Flag.File)
        pos(Type.DATA, "logger/kernel.log", Flag.File)
        pos(Type.DATA, "logger/kernel.log.1", Flag.File)
        pos(Type.DATA, "logger/last_log", Flag.File)
        pos(Type.DATA, "logger/setup/something_thing", Flag.File)
        pos(Type.DATA, "logger/setup/something_thing.log", Flag.File)

        neg(Type.DATA, "log", Flag.Dir)
        neg(Type.DATA, "log", Flag.File)
        neg(Type.DATA, "log/acore", Flag.Dir)
        neg(Type.DATA, "log/batterystats", Flag.Dir)
        neg(Type.DATA, "log/bt", Flag.Dir)
        neg(Type.DATA, "log/core", Flag.Dir)
        neg(Type.DATA, "log/err", Flag.Dir)
        neg(Type.DATA, "log/ewlogd", Flag.Dir)
        neg(Type.DATA, "log/imscr", Flag.Dir)
        neg(Type.DATA, "log/omc", Flag.Dir)
        neg(Type.DATA, "log/sepunion", Flag.Dir)
        neg(Type.DATA, "log/wifi", Flag.Dir)

        pos(Type.DATA, "log/0_com.samsung.android.bixby.service_bixbysearch_index.log", Flag.File)
        pos(Type.DATA, "log/dark_mode_log0.txt", Flag.File)
        pos(Type.DATA, "log/power_off_reset_reason.txt", Flag.File)
        pos(Type.DATA, "log/$rngString", Flag.File)

        neg(Type.DATA, "log_other_mode", Flag.Dir)
        neg(Type.DATA, "log_other_mode", Flag.File)
        neg(Type.DATA, "log_other_mode/$rngString", Flag.Dir)
        pos(Type.DATA, "log_other_mode/$rngString", Flag.File)
        neg(Type.DATA, "log_other_mode/subfolder", Flag.Dir)
        pos(Type.DATA, "log_other_mode/subfolder/$rngString", Flag.File)

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