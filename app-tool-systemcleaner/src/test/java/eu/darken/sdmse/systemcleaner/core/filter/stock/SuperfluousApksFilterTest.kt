package eu.darken.sdmse.systemcleaner.core.filter.stock

import eu.darken.sdmse.common.areas.DataArea.Type.SDCARD
import eu.darken.sdmse.common.areas.currentAreas
import eu.darken.sdmse.common.files.joinSegments
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilterTest
import eu.darken.sdmse.systemcleaner.core.sieve.SystemCrawlerSieve
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.mockDataStoreValue

class SuperfluousApksFilterTest : SystemCleanerFilterTest() {
    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create(includeSameVersion: Boolean = true) = SuperfluousApksFilter(
        sieveFactory = object : SystemCrawlerSieve.Factory {
            override fun create(config: SystemCrawlerSieve.Config): SystemCrawlerSieve =
                SystemCrawlerSieve(config, fileForensics)
        },
        pkgOps = pkgOps,
        pkgRepo = pkgRepo,
        gatewaySwitch = gatewaySwitch,
        cacheRepo = cacheRepo,
        settings = mockk<eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings>().apply {
            every { filterSuperfluosApksIncludeSameVersion } returns mockDataStoreValue(includeSameVersion)
        },
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

    @Test fun `testFilter excludes same version when setting disabled`() = runTest {
        mockDefaults()

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
        }

        neg(SDCARD, "Download", Flag.Dir)
        pos(SDCARD, "Download/older.apk", Flag.File)  // Still matched: installed (2) > archive (1)
        neg(SDCARD, "Download/equal.apk", Flag.File)  // NOT matched: installed (2) is not > archive (2)
        neg(SDCARD, "Download/newer.apk", Flag.File)  // Not matched: installed (2) < archive (3)

        confirm(create(includeSameVersion = false))
    }

}