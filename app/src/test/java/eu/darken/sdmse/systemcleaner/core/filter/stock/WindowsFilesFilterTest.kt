package eu.darken.sdmse.systemcleaner.core.filter.stock

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.currentAreas
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilterTest
import eu.darken.sdmse.systemcleaner.core.sieve.BaseSieve
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
            .distinctBy { it.type }
            .forEach {
                log { "Creating mocks for ${it.path}" }
                val loc = it.type
                pos(loc, "desktop.ini", Flag.File)
                val rngDir = rngString
                neg(loc, rngDir, Flag.Dir)
                pos(loc, "$rngDir/desktop.ini", Flag.File)
                pos(loc, "thumbs.db", Flag.File)
                pos(loc, "$rngDir/thumbs.db", Flag.File)
            }
        neg(DataArea.Type.DATA, "._rollkuchen#,'Ä", Flag.File)
        neg(DataArea.Type.DATA, "folder", Flag.Dir)
        neg(DataArea.Type.DATA, "folder/._rollkuchen#,'Ä", Flag.File)
        neg(DataArea.Type.DATA, "thumbs.db", Flag.File)
        confirm(create())
    }
}