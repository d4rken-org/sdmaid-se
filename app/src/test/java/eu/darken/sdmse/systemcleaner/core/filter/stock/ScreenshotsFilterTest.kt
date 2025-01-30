package eu.darken.sdmse.systemcleaner.core.filter.stock

import eu.darken.sdmse.common.areas.DataArea.Type.SDCARD
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilterTest
import eu.darken.sdmse.systemcleaner.core.sieve.BaseSieve
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration

class ScreenshotsFilterTest : SystemCleanerFilterTest() {

    private val settings = mockk<SystemCleanerSettings>().apply {
        every { filterScreenshotsAge } returns mockk<DataStoreValue<Duration>>().apply {
            every { flow } returns flowOf(Duration.ofDays(11))
        }
    }

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = ScreenshotsFilter(
        baseSieveFactory = object : BaseSieve.Factory {
            override fun create(config: BaseSieve.Config): BaseSieve = BaseSieve(config, fileForensics)
        },
        gatewaySwitch = gatewaySwitch,
        settings = settings,
    )

    @Test fun testFilter() = runTest {
        mockDefaults()
        neg(SDCARD, "Pictures/Screenshots", Flag.Dir)
        pos(SDCARD, "Pictures/Screenshots/123ABC.png", Flag.File)
        pos(SDCARD, "Pictures/Screenshots/456DEF.jpg", Flag.File)
        confirm(create())
    }
}
