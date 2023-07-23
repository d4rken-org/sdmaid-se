package eu.darken.sdmse.systemcleaner.core.filter.generic

import eu.darken.sdmse.common.areas.DataArea.Type
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

    @Test fun `test basic protected dirs`() = runTest {
        mockNegative(Type.SDCARD, "afile", Flags.FILE)
        mockPositive(Type.SDCARD, "SomethingelseSDCARD", Flags.DIR)
        mockNegative(Type.PUBLIC_MEDIA, "afile", Flags.FILE)
        mockNegative(Type.PUBLIC_MEDIA, "emptytopleveldir", Flags.DIR)
        mockNegative(Type.PUBLIC_MEDIA, "topleveldir", Flags.DIR)
        mockPositive(Type.PUBLIC_MEDIA, "topleveldir/emptybottomleveldir", Flags.DIR)
        mockNegative(Type.PUBLIC_DATA, "afile", Flags.FILE)
        mockNegative(Type.PUBLIC_DATA, "anotemptydir", Flags.DIR)
        mockNegative(Type.PUBLIC_DATA, "com.some.package", Flags.DIR)
        mockNegative(Type.PUBLIC_DATA, "com.some.package/files", Flags.DIR)
        mockNegative(Type.PUBLIC_DATA, "com.some.package/cache", Flags.DIR)
        mockNegative(Type.SDCARD, "DCIM", Flags.DIR)
        mockPositive(Type.SDCARD, "DCIM/EmptyDir", Flags.DIR)
        mockNegative(Type.SDCARD, "Camera", Flags.DIR)
        mockPositive(Type.SDCARD, "Camera/EmptyDir", Flags.DIR)
        mockNegative(Type.SDCARD, "Photos", Flags.DIR)
        mockPositive(Type.SDCARD, "Photos/EmptyDir", Flags.DIR)
        mockNegative(Type.SDCARD, "Music", Flags.DIR)
        mockPositive(Type.SDCARD, "Music/EmptyDir", Flags.DIR)
        mockNegative(Type.SDCARD, "Pictures", Flags.DIR)
        mockPositive(Type.SDCARD, "Pictures/EmptyDir", Flags.DIR)

//        // https://github.com/d4rken/sdmaid-public/issues/1435
        mockNegative(Type.SDCARD, ".stfolder", Flags.DIR)

        confirm(create())
    }

    @Test fun `empty directories - basic`() = runTest {
        mockNegative(Type.SDCARD, "1", Flags.DIR)
        mockNegative(Type.SDCARD, "1/1", Flags.FILE)
        mockPositive(Type.SDCARD, "2", Flags.DIR)
        mockPositive(Type.SDCARD, "2/2", Flags.DIR)

        confirm(create())
    }

    @Test fun `empty directories - nested`() = runTest {
        mockNegative(Type.SDCARD, "1", Flags.FILE)
        mockNegative(Type.SDCARD, "2", Flags.DIR)
        mockNegative(Type.SDCARD, "2/2", Flags.FILE)

        mockPositive(Type.SDCARD, "3", Flags.DIR)
        mockPositive(Type.SDCARD, "3/3", Flags.DIR)
        mockPositive(Type.SDCARD, "3/3/3", Flags.DIR)

        mockPositive(Type.SDCARD, "4", Flags.DIR)
        mockPositive(Type.SDCARD, "4/5", Flags.DIR)
        mockPositive(Type.SDCARD, "4/5/5", Flags.DIR)
        mockPositive(Type.SDCARD, "4/6", Flags.DIR)
        mockPositive(Type.SDCARD, "4/6/6", Flags.DIR)
        confirm(create())
    }

    @Test fun `empty directories - nested but blocked`() = runTest {
        mockNegative(Type.SDCARD, "4", Flags.DIR)
        mockNegative(Type.SDCARD, "4/file", Flags.FILE)

        mockPositive(Type.SDCARD, "4/5", Flags.DIR)
        mockPositive(Type.SDCARD, "4/5/5", Flags.DIR)

        mockNegative(Type.SDCARD, "4/6", Flags.DIR)
        mockNegative(Type.SDCARD, "4/6/6", Flags.DIR)
        mockNegative(Type.SDCARD, "4/6/6/file", Flags.FILE)

        confirm(create())
    }
}