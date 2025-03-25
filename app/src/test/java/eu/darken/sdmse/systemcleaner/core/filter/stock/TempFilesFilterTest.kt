package eu.darken.sdmse.systemcleaner.core.filter.stock

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataArea.Type.PRIVATE_DATA
import eu.darken.sdmse.common.areas.DataArea.Type.PUBLIC_DATA
import eu.darken.sdmse.common.areas.DataArea.Type.PUBLIC_MEDIA
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilterTest
import eu.darken.sdmse.systemcleaner.core.sieve.SystemCrawlerSieve
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TempFilesFilterTest : SystemCleanerFilterTest() {

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = TempFilesFilter(
        sieveFactory = object : SystemCrawlerSieve.Factory {
            override fun create(config: SystemCrawlerSieve.Config): SystemCrawlerSieve =
                SystemCrawlerSieve(config, fileForensics)
        },
        gatewaySwitch = gatewaySwitch,
    )

    @Test fun testFilter() = runTest {
        mockDefaults()
        // Legacy - we no longer match these
        setOf(PUBLIC_DATA, PRIVATE_DATA, PUBLIC_MEDIA).forEach {
            neg(it, "backup", Flag.Dir)
            neg(it, "backup/pending", Flag.Dir)
            neg(it, "backup/pending/bad.tmp", Flag.Dir)
            neg(it, "backup/pending/bad.temp", Flag.Dir)
            neg(it, "cache", Flag.Dir)
            neg(it, "cache/recovery", Flag.Dir)
            neg(it, "cache/recovery/bad.tmp", Flag.Dir)
            neg(it, "cache/recovery/bad.temp", Flag.Dir)
            neg(it, "com.drweb.pro.market", Flag.Dir)
            neg(it, "com.drweb.pro.market/files", Flag.Dir)
            neg(it, "com.drweb.pro.market/files/pro_settings", Flag.Dir)
            neg(it, "com.drweb.pro.market/files/pro_settings/bad.tmp", Flag.Dir)
            neg(it, "com.drweb.pro.market/files/pro_settings/bad.temp", Flag.Dir)
            neg(it, ".tmp", Flag.Dir)
            neg(it, ".temp", Flag.Dir)
            neg(it, "$rngString.tmp", Flag.File)
            neg(it, "$rngString.temp", Flag.File)
            neg(it, ".escheck.tmp", Flag.File)
            neg(it, ".escheck.temp", Flag.File)
            neg(it, "nested", Flag.Dir)
            neg(it, "nested/$rngString.tmp", Flag.File)
            neg(it, "nested/$rngString.temp", Flag.File)
            neg(it, ".mmsyscache", Flag.File)
            neg(it, "sdm_write_test-9d2542dc-a7fa-4c31-b5be-ad16c6a2d45c", Flag.File)
        }

        setOf(
            DataArea.Type.SDCARD,
            DataArea.Type.DATA,
            DataArea.Type.DATA_SYSTEM,
            DataArea.Type.DATA_SYSTEM_DE
        ).forEach {
            neg(it, "backup", Flag.Dir)
            neg(it, "backup/pending", Flag.Dir)
            neg(it, "backup/pending/bad.tmp", Flag.Dir)
            neg(it, "backup/pending/bad.temp", Flag.Dir)
            neg(it, "cache", Flag.Dir)
            neg(it, "cache/recovery", Flag.Dir)
            neg(it, "cache/recovery/bad.tmp", Flag.Dir)
            neg(it, "cache/recovery/bad.temp", Flag.Dir)
            neg(it, "com.drweb.pro.market", Flag.Dir)
            neg(it, "com.drweb.pro.market/files", Flag.Dir)
            neg(it, "com.drweb.pro.market/files/pro_settings", Flag.Dir)
            neg(it, "com.drweb.pro.market/files/pro_settings/bad.tmp", Flag.Dir)
            neg(it, "com.drweb.pro.market/files/pro_settings/bad.temp", Flag.Dir)
            neg(it, ".tmp", Flag.Dir)
            neg(it, ".temp", Flag.Dir)
            pos(it, "$rngString.tmp", Flag.File)
            pos(it, "$rngString.temp", Flag.File)
            pos(it, ".escheck.tmp", Flag.File)
            pos(it, ".escheck.temp", Flag.File)
            neg(it, "nested", Flag.Dir)
            pos(it, "nested/$rngString.tmp", Flag.File)
            pos(it, "nested/$rngString.temp", Flag.File)
            pos(it, ".mmsyscache", Flag.File)
            pos(it, "sdm_write_test-9d2542dc-a7fa-4c31-b5be-ad16c6a2d45c", Flag.File)
        }
        confirm(create())
    }

}
