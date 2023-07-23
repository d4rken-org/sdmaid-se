package eu.darken.sdmse.systemcleaner.core.filter.generic

import eu.darken.sdmse.common.areas.DataArea.Type
import eu.darken.sdmse.common.areas.currentAreas
import eu.darken.sdmse.systemcleaner.core.BaseSieve
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilterTest
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
        areaManager = areaManager,
    )

    @Test fun testFilter() = runTest {
        mockDefaults()
        val areas = setOf(Type.SDCARD, Type.PUBLIC_DATA)
        areaManager.currentAreas()
            .filter { areas.contains(it.type) }
            .distinctBy { it.type }
            .onEach {
                mockNegative(it.type, "bugsense", Flags.DIR)
                mockNegative(it.type, ".bugsense", Flags.DIR)
                mockPositive(it.type, ".bugsense", Flags.FILE)
            }
        confirm(create())
    }

}