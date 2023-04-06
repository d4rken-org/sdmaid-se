package eu.darken.sdmse.common.forensics.csi.source.tools

import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.pkgs.container.ApkInfo
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.pkgs.toPkgId
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import testhelpers.BaseTest

class DirectApkCheckTest : BaseTest() {

    @MockK lateinit var pkgRepo: eu.darken.sdmse.common.pkgs.PkgRepo
    @MockK lateinit var pkgOps: PkgOps

    @Before fun setup() {
        MockKAnnotations.init(this)
        coEvery { pkgRepo.isInstalled(any()) } returns false
    }

    private fun create() = DirectApkCheck(pkgOps, pkgRepo)

    @Test fun testBaseMatch() = runTest {
        val areaInfo = mockk<AreaInfo>().apply {
            every { file } returns LocalPath.build("com.mxtech.ffmpeg.x86", "something.apk")
        }
        val testPkg = "com.mxtech.ffmpeg.x86".toPkgId()
        coEvery { pkgRepo.isInstalled(testPkg) } returns true
        coEvery { pkgOps.viewArchive(any(), any()) } returns mockk<ApkInfo>().apply {
            every { id } returns testPkg
        }

        coEvery { pkgRepo.isInstalled(testPkg) } returns true
        create().process(areaInfo).apply {
            owners.single().pkgId shouldBe testPkg
        }
        coEvery { pkgRepo.isInstalled(testPkg) } returns false
        create().process(areaInfo).apply {
            owners.single().pkgId shouldBe testPkg
        }
    }
}