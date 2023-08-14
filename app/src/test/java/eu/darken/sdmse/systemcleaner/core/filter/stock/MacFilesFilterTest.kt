package eu.darken.sdmse.systemcleaner.core.filter.stock

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.currentAreas
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilterTest
import eu.darken.sdmse.systemcleaner.core.sieve.BaseSieve
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
            .distinctBy { it.type }
            .forEach {
                val loc = it.type
                neg(loc, "folder", Flag.Dir)
                pos(loc, "._something", Flag.File)
                pos(loc, "folder/._something", Flag.File)
                pos(loc, "._rollkuchen#,'Ä", Flag.File)
                pos(loc, "folder/._rollkuchen#,'Ä", Flag.File)
                pos(loc, ".Trashes", Flag.File)
                pos(loc, "folder/.Trashes", Flag.File)
                pos(loc, "._.Trashes", Flag.File)
                pos(loc, "folder/._.Trashes", Flag.File)
                pos(loc, ".spotlight", Flag.File)
                pos(loc, "folder/.spotlight", Flag.File)
                pos(loc, ".Spotlight-V100", Flag.File)
                pos(loc, "folder/.Spotlight-V100", Flag.File)
                pos(loc, ".DS_Store", Flag.File)
                pos(loc, "folder/.DS_Store", Flag.File)
                pos(loc, ".fseventsd", Flag.File)
                pos(loc, "folder/.fseventsd", Flag.File)
                pos(loc, ".TemporaryItems", Flag.File)
                pos(loc, "folder/.TemporaryItems", Flag.File)
            }
        neg(DataArea.Type.PUBLIC_DATA, "._rollkuchen#,'Ä", Flag.File)
        neg(DataArea.Type.PUBLIC_DATA, "some.pkg", Flag.Dir)
        neg(DataArea.Type.PUBLIC_DATA, "some.pkg/._rollkuchen#,'Ä", Flag.File)
        confirm(create())
    }
}
