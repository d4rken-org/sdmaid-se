package eu.darken.sdmse.systemcleaner.core.filter.generic

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.currentAreas
import eu.darken.sdmse.common.randomString
import eu.darken.sdmse.systemcleaner.core.BaseSieve
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilterTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WindowsFilesFilterTest : SystemCleanerFilterTest() {

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = WindowsFilesFilter(
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
                mockPositive(loc, "desktop.ini", Flags.FILE)
                mockPositive(loc, "${randomString()}/desktop.ini", Flags.FILE)
                mockPositive(loc, "thumbs.db", Flags.FILE)
                mockPositive(loc, "${randomString()}/thumbs.db", Flags.FILE)
            }
        mockNegative(DataArea.Type.DATA, "._rollkuchen#,'Ä", Flags.FILE)
        confirm(create())
    }
}