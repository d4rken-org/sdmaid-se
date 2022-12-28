package eu.darken.sdmse.systemcleaner.core.filter.generic

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.systemcleaner.core.BaseSieve
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilterTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LinuxFoldersFilterTest : SystemCleanerFilterTest() {

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = LinuxFilesFilter(
        baseSieveFactory = object : BaseSieve.Factory {
            override fun create(config: BaseSieve.Config): BaseSieve = BaseSieve(config, fileForensics)
        },
        areaManager = areaManager,
    )

    @Test fun testFilter() = runTest {
        mockDefaults()
        mockPositive(DataArea.Type.SDCARD, "/.Trash", Flags.DIR)
        mockPositive(DataArea.Type.SDCARD, "/.Trash-0", Flags.DIR)
        mockPositive(DataArea.Type.SDCARD, "/.Trash-11", Flags.DIR)
        mockPositive(DataArea.Type.SDCARD, "/.Trash-222", Flags.DIR)
        mockPositive(DataArea.Type.SDCARD, "/.Trash-1000", Flags.DIR)
        confirm(create())
    }
}