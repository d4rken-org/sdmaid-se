package eu.darken.sdmse.systemcleaner.core.filter.stock

import eu.darken.sdmse.common.areas.DataArea.Type.DATA
import eu.darken.sdmse.common.areas.DataArea.Type.DATA_MISC
import eu.darken.sdmse.common.areas.DataArea.Type.DATA_SYSTEM
import eu.darken.sdmse.common.areas.DataArea.Type.DATA_SYSTEM_CE
import eu.darken.sdmse.common.areas.DataArea.Type.DATA_VENDOR
import eu.darken.sdmse.common.areas.DataArea.Type.PRIVATE_DATA
import eu.darken.sdmse.common.areas.DataArea.Type.SDCARD
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilterTest
import eu.darken.sdmse.systemcleaner.core.sieve.SystemCrawlerSieve
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

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
        sieveFactory = object : SystemCrawlerSieve.Factory {
            override fun create(config: SystemCrawlerSieve.Config): SystemCrawlerSieve =
                SystemCrawlerSieve(config, fileForensics)
        },
        gatewaySwitch = gatewaySwitch,
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
        neg(PRIVATE_DATA, "something", Flag.Dir)
        neg(PRIVATE_DATA, "something/pmc", Flag.Dir)
        neg(PRIVATE_DATA, "something/pmc/db", Flag.Dir)
        neg(PRIVATE_DATA, "something/pmc/db/123.log", Flag.File)
        neg(SDCARD, "something", Flag.Dir)
        pos(SDCARD, "something/123.log", Flag.File)
        confirm(create())
    }

    @Test fun `extended radiologs`() = runTest {
        mockDefaults()

        neg(DATA_VENDOR, "radio", Flag.Dir)
        neg(DATA_VENDOR, "radio/extended_logs", Flag.Dir)
        neg(DATA_VENDOR, "radio/extended_logs/extended_log_something.txt", Flag.File)
        pos(DATA_VENDOR, "radio/extended_logs/extended_log_something.txt.old", Flag.File)
        confirm(create())
    }

    @Test fun `bluetooth logs`() = runTest {
        mockDefaults()

        neg(DATA_VENDOR, "bluetooth", Flag.Dir)
        neg(DATA_VENDOR, "bluetooth/bt_activity_pkt.txt", Flag.File)
        pos(DATA_VENDOR, "bluetooth/bt_activity_pkt.txt.last", Flag.File)
        confirm(create())
    }

    @Test fun `shutdown checkpoints`() = runTest {
        mockDefaults()

        neg(DATA_SYSTEM, "shutdown-checkpoints", Flag.Dir)
        neg(DATA_SYSTEM, "shutdown-checkpoints/checkpoints", Flag.File)
        pos(DATA_SYSTEM, "shutdown-checkpoints/checkpoints-1737469199197", Flag.File)
        confirm(create())
    }

    @Test fun `update engine logs`() = runTest {
        mockDefaults()

        neg(DATA_MISC, "update_engine_log", Flag.Dir)
        neg(
            DATA_MISC,
            "update_engine_log/update_engine.20250205-170816",
            Flag.File,
            Flag.LastModified(Instant.now())
        )
        pos(
            DATA_MISC,
            "update_engine_log/update_engine.20250202-170816",
            Flag.File,
            Flag.LastModified(Instant.now().minus(Duration.ofDays(3)))
        )
        confirm(create())
    }

    @Test fun `recovery logs`() = runTest {
        mockDefaults()

        neg(DATA_MISC, "recovery", Flag.Dir)
        pos(DATA_MISC, "recovery/last_kmsg.9", Flag.File)
        confirm(create())
    }

    @Test fun `miui log`() = runTest {
        mockDefaults()

        neg(DATA, "miuilog", Flag.Dir)
        neg(DATA, "miuilog/stability", Flag.Dir)
        neg(DATA, "miuilog/stability/scout", Flag.Dir)
        neg(DATA, "miuilog/stability/scout/app", Flag.Dir)
        pos(
            DATA,
            "miuilog/stability/scout/app/2025-02-17-06-45-26-com.android.vending-7230-APP_SCOUT_WARNING",
            Flag.Dir
        )
        pos(
            DATA,
            "miuilog/stability/scout/app/2025-02-17-06-45-26-com.android.vending-7230-APP_SCOUT_WARNING/com.android.vending-7230-APP_SCOUT_WARNING-2025-02-17-06-45-26-critical-section-trace",
            Flag.File
        )
        confirm(create())
    }
}