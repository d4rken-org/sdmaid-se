package eu.darken.sdmse.systemcleaner.core.filter.stock

import eu.darken.sdmse.common.areas.currentAreas
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilterTest
import eu.darken.sdmse.systemcleaner.core.sieve.BaseSieve
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
            .onEach {
                neg(it.type, "backup", Flag.Dir)
                neg(it.type, "backup/pending", Flag.Dir)
                neg(it.type, "backup/pending/bad.tmp", Flag.Dir)
                neg(it.type, "backup/pending/bad.temp", Flag.Dir)
                neg(it.type, "cache", Flag.Dir)
                neg(it.type, "cache/recovery", Flag.Dir)
                neg(it.type, "cache/recovery/bad.tmp", Flag.Dir)
                neg(it.type, "cache/recovery/bad.temp", Flag.Dir)
                neg(it.type, "com.drweb.pro.market", Flag.Dir)
                neg(it.type, "com.drweb.pro.market/files", Flag.Dir)
                neg(it.type, "com.drweb.pro.market/files/pro_settings", Flag.Dir)
                neg(it.type, "com.drweb.pro.market/files/pro_settings/bad.tmp", Flag.Dir)
                neg(it.type, "com.drweb.pro.market/files/pro_settings/bad.temp", Flag.Dir)
                neg(it.type, ".tmp", Flag.Dir)
                neg(it.type, ".temp", Flag.Dir)
                pos(it.type, "$rngString.tmp", Flag.File)
                pos(it.type, "$rngString.temp", Flag.File)
                pos(it.type, ".escheck.tmp", Flag.File)
                pos(it.type, ".escheck.temp", Flag.File)
                neg(it.type, "nested", Flag.Dir)
                pos(it.type, "nested/$rngString.tmp", Flag.File)
                pos(it.type, "nested/$rngString.temp", Flag.File)
                pos(it.type, ".mmsyscache", Flag.File)
                pos(it.type, "sdm_write_test-9d2542dc-a7fa-4c31-b5be-ad16c6a2d45c", Flag.File)
            }
        confirm(create())
    }
}
