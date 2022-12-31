package eu.darken.sdmse.common.forensics.csi.source.tools

import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.toPkgId
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import testhelpers.BaseTest

class SimilarityCheckTest : BaseTest() {

    private val pkgRepo: eu.darken.sdmse.common.pkgs.PkgRepo = mockk<eu.darken.sdmse.common.pkgs.PkgRepo>()

    private val areaInfo = mockk<AreaInfo>().apply {
        every { prefix } returns LocalPath.build("/abc")
        every { prefixFreePath } returns listOf("testdir-1")
    }

    private fun create() = SimilarityFilter(
        pkgRepo = pkgRepo,
    )

    @Test fun `false overlap, remove one`() = runTest {
        val pkgA = "pkgA".toPkgId()
        val pkgB = "pkgB".toPkgId()
        pkgRepo.apply {
            coEvery { getPkg(pkgA) } returns mockk<Installed>().apply {
                every { sourceDir } returns LocalPath.build("/abc/testdir-1")
            }
            coEvery { getPkg(pkgB) } returns mockk<Installed>().apply {
                every { sourceDir } returns LocalPath.build("/abc/testdir-2")
            }
        }

        val owners = setOf(
            Owner(pkgA),
            Owner(pkgB)
        )
        create().filterFalsePositives(areaInfo, owners).apply {
            map { it.pkgId } shouldBe setOf("pkgA".toPkgId())
        }
    }

    @Test fun `nothing installed, removing nothing`() = runTest {
        val pkgA = "pkgA".toPkgId()
        val pkgB = "pkgB".toPkgId()
        pkgRepo.apply {
            coEvery { getPkg(any()) } returns null

        }

        val toCheck = setOf(
            Owner(pkgA),
            Owner(pkgB)
        )

        create().filterFalsePositives(areaInfo, toCheck) shouldBe toCheck
    }

    @Test fun `overlap requires sourcepath to be non null`() = runTest {
        val pkgA = "pkgA".toPkgId()
        val pkgB = "pkgB".toPkgId()
        pkgRepo.apply {
            coEvery { getPkg(pkgA) } returns mockk<Installed>().apply {
                every { sourceDir } returns null
            }
            coEvery { getPkg(pkgB) } returns mockk<Installed>().apply {
                every { sourceDir } returns null
            }
        }

        val owners = setOf(
            Owner(pkgA),
            Owner(pkgB)
        )

        create().filterFalsePositives(areaInfo, owners) shouldBe owners
    }
}