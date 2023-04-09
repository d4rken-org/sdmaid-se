package eu.darken.sdmse.common.forensics.csi.source.tools

import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.pkgs.container.ApkInfo
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.user.UserHandle2
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

    @MockK lateinit var pkgOps: PkgOps

    @Before fun setup() {
        MockKAnnotations.init(this)
    }

    private fun create() = DirectApkCheck(pkgOps)

    @Test fun testBaseMatch() = runTest {
        val areaInfo = mockk<AreaInfo>().apply {
            every { file } returns LocalPath.build("com.mxtech.ffmpeg.x86", "something.apk")
            every { userHandle } returns UserHandle2(0)
        }
        val testPkg = "com.mxtech.ffmpeg.x86".toPkgId()
        coEvery { pkgOps.viewArchive(any(), any()) } returns mockk<ApkInfo>().apply {
            every { id } returns testPkg
        }

        create().process(areaInfo).apply {
            owners.single().pkgId shouldBe testPkg
        }

        create().process(areaInfo).apply {
            owners.single().pkgId shouldBe testPkg
        }
    }
}