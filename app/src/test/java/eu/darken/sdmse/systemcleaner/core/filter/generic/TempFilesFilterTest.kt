package eu.darken.sdmse.systemcleaner.core.filter.generic

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.currentAreas
import eu.darken.sdmse.common.randomString
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
        val areas = setOf(DataArea.Type.SDCARD, DataArea.Type.PUBLIC_MEDIA, DataArea.Type.PUBLIC_DATA)
        areaManager.currentAreas()
            .filter { areas.contains(it.type) }
            .onEach {
                mockNegative(it.type, "backup/pending/bad.tmp", Flags.DIR)
                mockNegative(it.type, "cache/recovery/bad.tmp", Flags.DIR)
                mockNegative(it.type, "com.drweb.pro.market/files/pro_settings/bad.tmp", Flags.DIR)
                mockNegative(it.type, "backup/pending/bad.temp", Flags.DIR)
                mockNegative(it.type, "cache/recovery/bad.temp", Flags.DIR)
                mockNegative(it.type, "com.drweb.pro.market/files/pro_settings/bad.temp", Flags.DIR)
                mockNegative(it.type, ".tmp", Flags.DIR)
                mockNegative(it.type, ".temp", Flags.DIR)
                mockPositive(it.type, "${randomString()}.tmp", Flags.FILE)
                mockPositive(it.type, "${randomString()}.temp", Flags.FILE)
                mockPositive(it.type, ".escheck.tmp", Flags.FILE)
                mockPositive(it.type, ".escheck.temp", Flags.FILE)
                mockPositive(it.type, "nested/${randomString()}.tmp", Flags.FILE)
                mockPositive(it.type, "nested/${randomString()}.temp", Flags.FILE)
            }
        confirm(create())
    }
}
