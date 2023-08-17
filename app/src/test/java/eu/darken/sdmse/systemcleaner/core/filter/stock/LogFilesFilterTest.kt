package eu.darken.sdmse.systemcleaner.core.filter.stock

import eu.darken.sdmse.common.areas.DataArea.Type.DATA
import eu.darken.sdmse.common.areas.DataArea.Type.DATA_SYSTEM_CE
import eu.darken.sdmse.common.areas.DataArea.Type.SDCARD
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilterTest
import eu.darken.sdmse.systemcleaner.core.sieve.BaseSieve
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
        neg(SDCARD, "$rngString.log", Flag.Dir)
        neg(SDCARD, ".log", Flag.Dir)

        neg(DATA, "/media", Flag.Dir)
        neg(DATA, "/media/0", Flag.Dir)
        neg(DATA, "/media/0/something.log", Flag.File)
        neg(DATA, "/media/1", Flag.Dir)
        neg(DATA, "/media/1/something.log", Flag.File)

        pos(SDCARD, "$rngString.log", Flag.File)
        neg(DATA_SYSTEM_CE, "com.google.android.gms", Flag.Dir)
        neg(DATA_SYSTEM_CE, "com.google.android.gms/snet", Flag.Dir)
        neg(DATA_SYSTEM_CE, "com.google.android.gms/snet/leveldb", Flag.Dir)
        neg(DATA_SYSTEM_CE, "com.google.android.gms/snet/leveldb/snet_sb_list_15", Flag.Dir)
        neg(DATA_SYSTEM_CE, "com.google.android.gms/snet/leveldb/snet_sb_list_15/000008.log", Flag.File)
        neg(SDCARD, "something.indexeddb.leveldb", Flag.Dir)
        neg(SDCARD, "something.indexeddb.leveldb/something.log", Flag.File)
        neg(SDCARD, "a", Flag.Dir)
        neg(SDCARD, "/t", Flag.Dir)
        neg(SDCARD, "a/t", Flag.Dir)
        neg(SDCARD, "/t/Paths", Flag.Dir)
        neg(SDCARD, "a/t/Paths", Flag.Dir)
        neg(SDCARD, "/t/Paths/000003.log", Flag.File)
        neg(SDCARD, "a/t/Paths/000003.log", Flag.File)
        neg(SDCARD, "/app_chrome", Flag.Dir)
        neg(SDCARD, "a/app_chrome", Flag.Dir)
        neg(SDCARD, "/app_chrome/Default", Flag.Dir)
        neg(SDCARD, "a/app_chrome/Default", Flag.Dir)
        neg(SDCARD, "/app_chrome/Default/previews_hint_cache_store", Flag.Dir)
        neg(SDCARD, "a/app_chrome/Default/previews_hint_cache_store", Flag.Dir)
        neg(SDCARD, "/app_chrome/Default/previews_hint_cache_store/000003.log", Flag.File)
        neg(SDCARD, "a/app_chrome/Default/previews_hint_cache_store/000003.log", Flag.File)
        neg(SDCARD, "/app_chrome/000003.log", Flag.File)
        neg(SDCARD, "a/app_chrome/000003.log", Flag.File)
        neg(SDCARD, "/app_webview", Flag.Dir)
        neg(SDCARD, "a/app_webview", Flag.Dir)
        neg(SDCARD, "/app_webview/something", Flag.Dir)
        neg(SDCARD, "a/app_webview/something", Flag.Dir)
        neg(SDCARD, "/app_webview/something/000003.log", Flag.File)
        neg(SDCARD, "a/app_webview/something/000003.log", Flag.File)
        confirm(create())
    }

    /**
     * https://github.com/d4rken/sdmaid-public/issues/2147
     */
    @Test fun testTelegramXFalsePositive() = runTest {
        mockDefaults()
        neg(SDCARD, "something", Flag.Dir)
        neg(SDCARD, "something/pmc", Flag.Dir)
        neg(SDCARD, "something/pmc/db", Flag.Dir)
        neg(SDCARD, "something/pmc/db/123.log", Flag.File)
        pos(SDCARD, "something/123.log", Flag.File)
        confirm(create())
    }
}