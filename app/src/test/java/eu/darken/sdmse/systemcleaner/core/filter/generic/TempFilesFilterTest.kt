package eu.darken.sdmse.systemcleaner.core.filter.generic

import eu.darken.sdmse.common.areas.currentAreas
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.systemcleaner.core.BaseSieve
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilterTest
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
                mockNegative(it.type, "backup", Flags.DIR)
                mockNegative(it.type, "backup/pending", Flags.DIR)
                mockNegative(it.type, "backup/pending/bad.tmp", Flags.DIR)
                mockNegative(it.type, "backup/pending/bad.temp", Flags.DIR)
                mockNegative(it.type, "cache", Flags.DIR)
                mockNegative(it.type, "cache/recovery", Flags.DIR)
                mockNegative(it.type, "cache/recovery/bad.tmp", Flags.DIR)
                mockNegative(it.type, "cache/recovery/bad.temp", Flags.DIR)
                mockNegative(it.type, "com.drweb.pro.market", Flags.DIR)
                mockNegative(it.type, "com.drweb.pro.market/files", Flags.DIR)
                mockNegative(it.type, "com.drweb.pro.market/files/pro_settings", Flags.DIR)
                mockNegative(it.type, "com.drweb.pro.market/files/pro_settings/bad.tmp", Flags.DIR)
                mockNegative(it.type, "com.drweb.pro.market/files/pro_settings/bad.temp", Flags.DIR)
                mockNegative(it.type, ".tmp", Flags.DIR)
                mockNegative(it.type, ".temp", Flags.DIR)
                mockPositive(it.type, "$rngString.tmp", Flags.FILE)
                mockPositive(it.type, "$rngString.temp", Flags.FILE)
                mockPositive(it.type, ".escheck.tmp", Flags.FILE)
                mockPositive(it.type, ".escheck.temp", Flags.FILE)
                mockNegative(it.type, "nested", Flags.DIR)
                mockPositive(it.type, "nested/$rngString.tmp", Flags.FILE)
                mockPositive(it.type, "nested/$rngString.temp", Flags.FILE)
                mockPositive(it.type, ".mmsyscache", Flags.FILE)
                mockPositive(it.type, "sdm_write_test-9d2542dc-a7fa-4c31-b5be-ad16c6a2d45c", Flags.FILE)
            }
        confirm(create())
    }
}
