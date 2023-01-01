package eu.darken.sdmse.systemcleaner.core.filter.generic

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.systemcleaner.core.BaseSieve
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilterTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EmptyDirectoryFilterTest : SystemCleanerFilterTest() {
    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = EmptyDirectoryFilter(
        baseSieveFactory = object : BaseSieve.Factory {
            override fun create(config: BaseSieve.Config): BaseSieve = BaseSieve(config, fileForensics)
        },
        areaManager = areaManager,
        gatewaySwitch = gatewaySwitch,
    )

    @Test fun testFilter() = runTest {
        mockDefaults()
        mockNegative(DataArea.Type.SDCARD, "afile", Flags.FILE, Flags.EMPTY)
        mockNegative(DataArea.Type.SDCARD, "anotemptydir", Flags.DIR)
        mockPositive(DataArea.Type.SDCARD, "SomethingelseSDCARD", Flags.DIR, Flags.EMPTY)
        mockNegative(DataArea.Type.PUBLIC_MEDIA, "afile", Flags.FILE, Flags.EMPTY)
        mockNegative(DataArea.Type.PUBLIC_MEDIA, "anotemptydir", Flags.DIR)
        mockNegative(DataArea.Type.PUBLIC_MEDIA, "emptytopleveldir", Flags.DIR, Flags.EMPTY)
        mockPositive(DataArea.Type.PUBLIC_MEDIA, "topleveldir/emptybottomleveldir", Flags.DIR, Flags.EMPTY)
        mockNegative(DataArea.Type.PUBLIC_DATA, "afile", Flags.FILE, Flags.EMPTY)
        mockNegative(DataArea.Type.PUBLIC_DATA, "anotemptydir", Flags.DIR)
        mockNegative(DataArea.Type.PUBLIC_DATA, "com.some.package/files", Flags.DIR, Flags.EMPTY)
        mockNegative(DataArea.Type.PUBLIC_DATA, "com.some.package/cache", Flags.DIR, Flags.EMPTY)
        mockNegative(DataArea.Type.PUBLIC_DATA, "com.some.package", Flags.DIR, Flags.EMPTY)
        mockNegative(DataArea.Type.SDCARD, "DCIM", Flags.DIR, Flags.EMPTY)
        mockPositive(DataArea.Type.SDCARD, "DCIM/EmptyDir", Flags.DIR, Flags.EMPTY)
        mockNegative(DataArea.Type.SDCARD, "Camera", Flags.DIR, Flags.EMPTY)
        mockPositive(DataArea.Type.SDCARD, "Camera/EmptyDir", Flags.DIR, Flags.EMPTY)
        mockNegative(DataArea.Type.SDCARD, "Photos", Flags.DIR, Flags.EMPTY)
        mockPositive(DataArea.Type.SDCARD, "Photos/EmptyDir", Flags.DIR, Flags.EMPTY)
        mockNegative(DataArea.Type.SDCARD, "Music", Flags.DIR, Flags.EMPTY)
        mockPositive(DataArea.Type.SDCARD, "Music/EmptyDir", Flags.DIR, Flags.EMPTY)
        mockNegative(DataArea.Type.SDCARD, "Pictures", Flags.DIR, Flags.EMPTY)
        mockPositive(DataArea.Type.SDCARD, "Pictures/EmptyDir", Flags.DIR, Flags.EMPTY)

//        // https://github.com/d4rken/sdmaid-public/issues/1435
        mockNegative(DataArea.Type.SDCARD, ".stfolder", Flags.DIR, Flags.EMPTY)

        confirm(create())
    }

}