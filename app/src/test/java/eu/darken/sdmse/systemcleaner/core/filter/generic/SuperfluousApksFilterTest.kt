package eu.darken.sdmse.systemcleaner.core.filter.generic

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.currentAreas
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.systemcleaner.core.BaseSieve
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilterTest
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
                    mockArchive(pkg, areaPath.child(exclusionFolder, "blabla.apk")).apply {
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

        mockPositive(DataArea.Type.SDCARD, "Download/older.apk", Flags.FILE)
        mockPositive(DataArea.Type.SDCARD, "Download/equal.apk", Flags.FILE)
        mockNegative(DataArea.Type.SDCARD, "Download/newer.apk", Flags.FILE)

        mockPositive(DataArea.Type.SDCARD, "Download/honey.apk", Flags.FILE)

        mockNegative(DataArea.Type.SDCARD, "Download/notafile.apk", Flags.DIR)

        mockPositive(DataArea.Type.SDCARD, "$randomFolder/blabla.apk", Flags.FILE)

        mockNegative(DataArea.Type.SDCARD, "ABCBackupDEF/honey.apk", Flags.FILE)
        SuperfluousApksFilter.EXCLUSIONS.forEach { exclusionFolder ->
            mockNegative(DataArea.Type.SDCARD, "$exclusionFolder/blabla.apk", Flags.FILE)
        }

        confirm(create())
    }

}