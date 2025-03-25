package eu.darken.sdmse.systemcleaner.core.filter.stock

import eu.darken.sdmse.common.areas.DataArea.Type.PUBLIC_DATA
import eu.darken.sdmse.common.areas.DataArea.Type.SDCARD
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilterTest
import eu.darken.sdmse.systemcleaner.core.sieve.SystemCrawlerSieve
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
        sieveFactory = object : SystemCrawlerSieve.Factory {
            override fun create(config: SystemCrawlerSieve.Config): SystemCrawlerSieve =
                SystemCrawlerSieve(config, fileForensics)
        },
        gatewaySwitch = gatewaySwitch,
    )

    @Test fun testFilter() = runTest {
        mockDefaults()
        neg(PUBLIC_DATA, "/.Trash", Flag.Dir)
        neg(SDCARD, "/.Trash", Flag.Dir)
        pos(SDCARD, "/.Trash/file", Flag.Dir)
        pos(SDCARD, "/.Trash-", Flag.Dir)
        pos(SDCARD, "/.Trash-/file", Flag.Dir)
        pos(SDCARD, "/.Trash-0", Flag.Dir)
        pos(SDCARD, "/.Trash-0/file", Flag.Dir)
        neg(SDCARD, "Android/.Trash-1000", Flag.Dir)
        confirm(create())
    }
}