package eu.darken.sdmse.common.forensics.csi.dalvik.tools

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.forensics.csi.dalvik.DalvikCheck
import eu.darken.sdmse.common.pkgs.toPkgId
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import testhelpers.BaseTest

class DirNameCheckTest : BaseTest() {
    private val testPkgId = "com.mxtech.ffmpeg.x86".toPkgId()
    private val areaInfo: AreaInfo = mockk<AreaInfo>().apply {
        every { type } returns DataArea.Type.DALVIK_PROFILE
        every { prefixFreePath } returns testPkgId.name
    }
    private val pkgRepo: eu.darken.sdmse.common.pkgs.PkgRepo = mockk<eu.darken.sdmse.common.pkgs.PkgRepo>().apply {
        coEvery { isInstalled(any()) } returns false
    }

    private fun create() = DirNameCheck(pkgRepo)

    @Test fun testNotInstalled() = runTest {
        val instance = create()

        instance.process(areaInfo) shouldBe DalvikCheck.Result()
    }

    @Test fun testBaseMatch() = runTest {
        coEvery { pkgRepo.isInstalled(testPkgId) } returns true
        val instance = create()

        instance.process(areaInfo) shouldBe DalvikCheck.Result(setOf(Owner(testPkgId)))
    }
}