package eu.darken.sdmse.systemcleaner.core.filter.generic

import eu.darken.sdmse.common.areas.DataArea.Type
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.systemcleaner.core.BaseSieve
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilterTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LogFilesFilterTest : SystemCleanerFilterTest() {

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = LogFilesFilter(
        baseSieveFactory = object : BaseSieve.Factory {
            override fun create(config: BaseSieve.Config): BaseSieve = BaseSieve(config, fileForensics)
        },
        areaManager = areaManager,
    )

    @Test fun testFilter() = runTest {
        mockDefaults()
        mockNegative(Type.SDCARD, "$rngString.log", Flags.DIR)
        mockNegative(Type.SDCARD, ".log", Flags.DIR)
        mockNegative(Type.DATA, "/media/0/something.log", Flags.FILE)
        mockNegative(Type.DATA, "/media/1/something.log", Flags.FILE)
        mockPositive(Type.SDCARD, "$rngString.log", Flags.FILE)
        mockNegative(Type.SDCARD, "something.indexeddb.leveldb/something.log", Flags.FILE)
        mockNegative(Type.SDCARD, "/t/Paths/000003.log", Flags.FILE)
        mockNegative(Type.SDCARD, "/app_chrome/Default/previews_hint_cache_store/000003.log", Flags.FILE)
        mockNegative(Type.SDCARD, "/app_chrome/000003.log", Flags.FILE)
        mockNegative(Type.SDCARD, "/app_webview/something/000003.log", Flags.FILE)
        confirm(create())
    }

    /**
     * https://github.com/d4rken/sdmaid-public/issues/2147
     */
    @Test fun testTelegramXFalsePositive() = runTest {
        mockDefaults()
        mockNegative(Type.SDCARD, "something/pmc/db/123.log", Flags.FILE)
        mockPositive(Type.SDCARD, "something/123.log", Flags.FILE)
        confirm(create())
    }
}