package eu.darken.sdmse.systemcleaner.core.filter.stock

import eu.darken.sdmse.common.areas.DataArea.Type.SDCARD
import eu.darken.sdmse.common.areas.currentAreas
import eu.darken.sdmse.common.files.joinSegments
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilterTest
import eu.darken.sdmse.systemcleaner.core.sieve.BaseSieve
import io.mockk.every
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SuperfluousApksFilterTest : SystemCleanerFilterTest() {
    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = SuperfluousApksFilter(
        baseSieveFactory = object : BaseSieve.Factory {
            override fun create(config: BaseSieve.Config): BaseSieve = BaseSieve(config, fileForensics)
        },
        pkgOps = pkgOps,
        pkgRepo = pkgRepo,
        areaManager = areaManager,
    )

    @Test fun testFilter() = runTest {
        mockDefaults()

        val randomFolder = rngString
        areaManager.currentAreas().map { area ->
            val areaPath = area.path

            "packageName1".toPkgId().let { pkg ->
                mockArchive(pkg, areaPath.child("Download", "older.apk")).apply {
                    every { versionCode } returns 1L
                }
                mockArchive(pkg, areaPath.child("Download", "equal.apk")).apply {
                    every { versionCode } returns 2L
                }
                mockArchive(pkg, areaPath.child("Download", "newer.apk")).apply {
                    every { versionCode } returns 3L
                }
                mockPkg(pkg).apply {
                    every { versionCode } returns 2L
                }
            }

            "packageName2".toPkgId().let { pkg ->
                mockArchive(pkg, areaPath.child(randomFolder, "blabla.apk")).apply {
                    every { versionCode } returns 2L
                }
                SuperfluousApksFilter.EXCLUSIONS.forEach { exclusionFolder ->
                    mockArchive(pkg, areaPath.child(*exclusionFolder.segments.toTypedArray(), "blabla.apk")).apply {
                        every { versionCode } returns 2L
                    }
                }

                mockArchive(pkg, areaPath.child("ABCBackupDEF", "blabla.apk")).apply {
                    every { versionCode } returns 2L
                }

                mockPkg(pkg).apply {
                    every { versionCode } returns 3L
                }
            }

            "packageName3".toPkgId().let { pkg ->
                mockArchive(pkg, areaPath.child("Download", "honey.apk")).apply {
                    every { versionCode } returns 2L
                }
                mockPkg(pkg).apply {
                    every { versionCode } returns Long.MAX_VALUE
                }
            }
        }
        neg(SDCARD, "Download", Flag.Dir)
        pos(SDCARD, "Download/older.apk", Flag.File)
        pos(SDCARD, "Download/equal.apk", Flag.File)
        neg(SDCARD, "Download/newer.apk", Flag.File)

        pos(SDCARD, "Download/honey.apk", Flag.File)

        neg(SDCARD, "Download/notafile.apk", Flag.Dir)

        neg(SDCARD, randomFolder, Flag.Dir)
        pos(SDCARD, "$randomFolder/blabla.apk", Flag.File)

        neg(SDCARD, "ABCBackupDEF", Flag.Dir)
        neg(SDCARD, "ABCBackupDEF/honey.apk", Flag.File)

        SuperfluousApksFilter.EXCLUSIONS.forEach { exclusionFolder ->
            val baseDir = exclusionFolder.segments.joinSegments()
            neg(SDCARD, baseDir, Flag.Dir)
            neg(SDCARD, "$baseDir/blabla.apk", Flag.File)
        }

        confirm(create())
    }

}