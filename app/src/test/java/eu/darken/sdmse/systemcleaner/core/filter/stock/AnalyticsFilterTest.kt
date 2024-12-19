package eu.darken.sdmse.systemcleaner.core.filter.stock

import eu.darken.sdmse.common.areas.DataArea.Type
import eu.darken.sdmse.common.areas.DataArea.Type.SDCARD
import eu.darken.sdmse.common.areas.currentAreas
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilterTest
import eu.darken.sdmse.systemcleaner.core.sieve.BaseSieve
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AnalyticsFilterTest : SystemCleanerFilterTest() {

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = AnalyticsFilter(
        baseSieveFactory = object : BaseSieve.Factory {
            override fun create(config: BaseSieve.Config): BaseSieve = BaseSieve(config, fileForensics)
        },
        gatewaySwitch = gatewaySwitch,
    )

    @Test fun testFilter() = runTest {
        mockDefaults()
        val areas = setOf(SDCARD, Type.PUBLIC_DATA)
        areaManager.currentAreas()
            .filter { areas.contains(it.type) }
            .distinctBy { it.type }
            .onEach {
                neg(it.type, "bugsense", Flag.Dir)
                neg(it.type, ".bugsense", Flag.Dir)
                pos(it.type, ".bugsense", Flag.File)
            }

        neg(SDCARD, "tlocalcookieid", Flag.File)
        pos(SDCARD, ".tlocalcookieid", Flag.File)
        neg(SDCARD, "INSTALLATION", Flag.File)
        pos(SDCARD, ".INSTALLATION", Flag.File)
        neg(SDCARD, "wps_preloaded_2.txt", Flag.File)
        pos(SDCARD, ".wps_preloaded_2.txt", Flag.File)
        confirm(create())
    }

}