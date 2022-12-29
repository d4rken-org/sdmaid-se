package eu.darken.sdmse.systemcleaner.core.filter.generic

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.currentAreas
import eu.darken.sdmse.systemcleaner.core.BaseSieve
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilterTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MacFilesFilterTest : SystemCleanerFilterTest() {

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = MacFilesFilter(
        baseSieveFactory = object : BaseSieve.Factory {
            override fun create(config: BaseSieve.Config): BaseSieve = BaseSieve(config, fileForensics)
        },
        areaManager = areaManager,
    )

    @Test fun testFilter() = runTest {
        mockDefaults()
        val areas = create().targetAreas()
        areaManager.currentAreas()
            .filter { areas.contains(it.type) }
            .forEach {
                val loc = it.type
                mockPositive(loc, "._something", Flags.FILE)
                mockPositive(loc, "._rollkuchen#,'Ä", Flags.FILE)
                mockPositive(loc, ".Trashes", Flags.FILE)
                mockPositive(loc, "._.Trashes", Flags.FILE)
                mockPositive(loc, ".spotlight", Flags.FILE)
                mockPositive(loc, ".Spotlight-V100", Flags.FILE)
                mockPositive(loc, ".DS_Store", Flags.FILE)
                mockPositive(loc, ".fseventsd", Flags.FILE)
                mockPositive(loc, ".TemporaryItems", Flags.FILE)
            }
        mockNegative(DataArea.Type.PUBLIC_DATA, "._rollkuchen#,'Ä", Flags.FILE)
        confirm(create())
    }
}
