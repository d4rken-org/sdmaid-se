package eu.darken.sdmse.systemcleaner.core.filter.stock

import eu.darken.sdmse.common.areas.DataArea.Type.PUBLIC_DATA
import eu.darken.sdmse.common.areas.DataArea.Type.PUBLIC_MEDIA
import eu.darken.sdmse.common.areas.DataArea.Type.SDCARD
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilterTest
import eu.darken.sdmse.systemcleaner.core.sieve.BaseSieve
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
        neg(SDCARD, "afile", Flag.File)
        pos(SDCARD, "SomethingelseSDCARD", Flag.Dir)
        neg(PUBLIC_MEDIA, "afile", Flag.File)
        neg(PUBLIC_MEDIA, "emptytopleveldir", Flag.Dir)
        neg(PUBLIC_MEDIA, "topleveldir", Flag.Dir)
        pos(PUBLIC_MEDIA, "topleveldir/emptybottomleveldir", Flag.Dir)
        neg(PUBLIC_DATA, "afile", Flag.File)
        neg(PUBLIC_DATA, "anotemptydir", Flag.Dir)
        neg(PUBLIC_DATA, "com.some.package", Flag.Dir)
        neg(PUBLIC_DATA, "com.some.package/files", Flag.Dir)
        neg(PUBLIC_DATA, "com.some.package/cache", Flag.Dir)
        neg(SDCARD, "DCIM", Flag.Dir)
        pos(SDCARD, "DCIM/EmptyDir", Flag.Dir)
        neg(SDCARD, "Camera", Flag.Dir)
        pos(SDCARD, "Camera/EmptyDir", Flag.Dir)
        neg(SDCARD, "Photos", Flag.Dir)
        pos(SDCARD, "Photos/EmptyDir", Flag.Dir)
        neg(SDCARD, "Music", Flag.Dir)
        pos(SDCARD, "Music/EmptyDir", Flag.Dir)
        neg(SDCARD, "Pictures", Flag.Dir)
        pos(SDCARD, "Pictures/EmptyDir", Flag.Dir)

//        // https://github.com/d4rken/sdmaid-public/issues/1435
        neg(SDCARD, ".stfolder", Flag.Dir)

        confirm(create())
    }

    @Test fun `empty directories - basic`() = runTest {
        neg(SDCARD, "1", Flag.Dir)
        neg(SDCARD, "1/1", Flag.File)
        pos(SDCARD, "2", Flag.Dir)
        pos(SDCARD, "2/2", Flag.Dir)

        confirm(create())
    }

    @Test fun `empty directories - nested`() = runTest {
        neg(SDCARD, "1", Flag.File)
        neg(SDCARD, "2", Flag.Dir)
        neg(SDCARD, "2/2", Flag.File)

        pos(SDCARD, "3", Flag.Dir)
        pos(SDCARD, "3/3", Flag.Dir)
        pos(SDCARD, "3/3/3", Flag.Dir)

        pos(SDCARD, "4", Flag.Dir)
        pos(SDCARD, "4/5", Flag.Dir)
        pos(SDCARD, "4/5/5", Flag.Dir)
        pos(SDCARD, "4/6", Flag.Dir)
        pos(SDCARD, "4/6/6", Flag.Dir)
        confirm(create())
    }

    @Test fun `empty directories - nested but blocked`() = runTest {
        neg(SDCARD, "4", Flag.Dir)
        neg(SDCARD, "4/file", Flag.File)

        pos(SDCARD, "4/5", Flag.Dir)
        pos(SDCARD, "4/5/5", Flag.Dir)

        neg(SDCARD, "4/6", Flag.Dir)
        neg(SDCARD, "4/6/6", Flag.Dir)
        neg(SDCARD, "4/6/6/file", Flag.File)

        confirm(create())
    }
}