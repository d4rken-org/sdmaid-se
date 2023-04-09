package eu.darken.sdmse.common.forensics.csi.source.tools

import android.content.pm.ApplicationInfo
import android.os.Bundle
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.pkgs.PkgRepo
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

class ApkDirCheckTest : BaseTest() {

    @MockK lateinit var pkgRepo: PkgRepo
    @MockK lateinit var pkgOps: PkgOps
    private val handle = UserHandle2(0)

    @Before fun setup() {
        MockKAnnotations.init(this)
        coEvery { pkgRepo.query(any(), any()) } returns emptySet()
        coEvery { pkgOps.viewArchive(any(), any()) } returns null
    }

    private fun create() = ApkDirCheck(pkgOps)

    @Test fun testDontMatch() = runTest {
        val areaInfo = mockk<AreaInfo>().apply {
            every { file } returns LocalPath.build("test")
            every { userHandle } returns handle
        }

        create().process(areaInfo).apply {
            owners.isEmpty() shouldBe true
            hasKnownUnknownOwner shouldBe false
        }
    }

    @Test fun `named apk`() = runTest {
        val areaInfo = mockk<AreaInfo>().apply {
            every { file } returns LocalPath.build("test")
            every { userHandle } returns handle
        }
        val testPkg = "com.mxtech.ffmpeg.x86".toPkgId()

        coEvery { pkgOps.viewArchive(any(), any()) } answers {
            if (arg<LocalPath>(0).name.endsWith("test.apk")) {
                mockk<ApkInfo>().apply {
                    every { id } returns testPkg
                    every { tryField<String?>(any()) } returns null
                    every { applicationInfo } returns null
                    every { requestedPermissions } returns emptySet()
                }
            } else {
                null
            }
        }

        create().process(areaInfo).apply {
            owners.single().pkgId shouldBe testPkg
            hasKnownUnknownOwner shouldBe false
        }
    }

    @Test fun `base apk`() = runTest {
        val areaInfo = mockk<AreaInfo>().apply {
            every { file } returns LocalPath.build("test")
            every { userHandle } returns handle
        }
        val testPkg = "com.mxtech.ffmpeg.x86".toPkgId()

        coEvery { pkgOps.viewArchive(any(), any()) } answers {
            if (arg<LocalPath>(0).name.endsWith("base.apk")) {
                mockk<ApkInfo>().apply {
                    every { id } returns testPkg
                    every { tryField<String?>(any()) } returns null
                    every { applicationInfo } returns null
                    every { requestedPermissions } returns emptySet()
                }
            } else {
                null
            }
        }

        create().process(areaInfo).apply {
            owners.single().pkgId shouldBe testPkg
            hasKnownUnknownOwner shouldBe false
        }
    }

    @Test fun testThemeCheck_pkginfo_overlay_reflection() = runTest {
        val areaInfo = mockk<AreaInfo>().apply {
            every { file } returns LocalPath.build("test")
            every { userHandle } returns handle
        }

        val pkg1 = "pkg1".toPkgId()
        val pkg2 = "pkg2".toPkgId()

        coEvery { pkgOps.viewArchive(any(), any()) } answers {
            if (arg<LocalPath>(0).name.endsWith("base.apk")) {
                mockk<ApkInfo>().apply {
                    every { id } returns pkg1
                    every { tryField<String?>("overlayTarget") } returns pkg2.name
                    every { applicationInfo } returns null
                    every { requestedPermissions } returns emptySet()
                }
            } else {
                null
            }
        }

        create().process(areaInfo).apply {
            owners shouldBe setOf(
                Owner(pkg1, handle),
                Owner(pkg2, handle)
            )
            hasKnownUnknownOwner shouldBe false
        }
    }

    @Test fun testThemeCheck_metaData_targetPkg() = runTest {
        val areaInfo = mockk<AreaInfo>().apply {
            every { file } returns LocalPath.build("test")
            every { userHandle } returns handle
        }

        val pkg1 = "pkg1".toPkgId()
        val pkg2 = "pkg2".toPkgId()
        val pkg3 = "pkg3".toPkgId()

        coEvery { pkgOps.viewArchive(any(), any()) } answers {
            if (arg<LocalPath>(0).name.endsWith("base.apk")) {
                mockk<ApkInfo>().apply {
                    every { id } returns pkg1
                    every { tryField<String?>("overlayTarget") } returns pkg2.name
                    every { requestedPermissions } returns emptySet()
                    every { applicationInfo } returns mockk<ApplicationInfo>().apply {
                        metaData = mockk<Bundle>().apply {
                            every { getString(any()) } returns null
                            every { getString("target_package") } returns pkg3.name
                        }
                    }
                }
            } else {
                null
            }
        }

        create().process(areaInfo).apply {
            owners shouldBe setOf(
                Owner(pkg1, handle),
                Owner(pkg2, handle),
                Owner(pkg3, handle),
            )
            hasKnownUnknownOwner shouldBe false
        }
    }

    @Test fun testThemeCheck_metaData_Substratum_targetPkg() = runTest {
        val areaInfo = mockk<AreaInfo>().apply {
            every { file } returns LocalPath.build("test")
            every { userHandle } returns handle
        }

        val pkg1 = "pkg1".toPkgId()
        val pkg2 = "pkg2".toPkgId()
        val pkg3 = "pkg3".toPkgId()
        val pkg4 = "pkg4".toPkgId()

        coEvery { pkgOps.viewArchive(any(), any()) } answers {
            if (arg<LocalPath>(0).name.endsWith("base.apk")) {
                mockk<ApkInfo>().apply {
                    every { id } returns pkg1
                    every { tryField<String?>("overlayTarget") } returns pkg2.name
                    every { requestedPermissions } returns emptySet()
                    every { applicationInfo } returns mockk<ApplicationInfo>().apply {
                        metaData = mockk<Bundle>().apply {
                            every { getString(any()) } returns null
                            every { getString("target_package") } returns pkg3.name
                            every { getString("Substratum_Target") } returns pkg4.name
                        }
                    }
                }
            } else {
                null
            }
        }

        create().process(areaInfo).apply {
            owners shouldBe setOf(
                Owner(pkg1, handle),
                Owner(pkg2, handle),
                Owner(pkg3, handle),
                Owner(pkg4, handle),
            )
            hasKnownUnknownOwner shouldBe false
        }
    }

    @Test fun testThemeCheck_SamsungPermission() = runTest {

        val areaInfo = mockk<AreaInfo>().apply {
            every { file } returns LocalPath.build("test")
            every { userHandle } returns handle
        }

        val pkg1 = "pkg1".toPkgId()
        val pkg2 = "pkg2".toPkgId()
        val pkg3 = "pkg3".toPkgId()
        val pkg4 = "pkg4".toPkgId()

        coEvery { pkgOps.viewArchive(any(), any()) } answers {
            if (arg<LocalPath>(0).name.endsWith("base.apk")) {
                mockk<ApkInfo>().apply {
                    every { id } returns pkg1
                    every { tryField<String?>("overlayTarget") } returns pkg2.name
                    every { requestedPermissions } returns setOf(
                        "com.samsung.android.permission.SAMSUNG_OVERLAY_APPICON"
                    )
                    every { applicationInfo } returns mockk<ApplicationInfo>().apply {
                        metaData = mockk<Bundle>().apply {
                            every { getString(any()) } returns null
                            every { getString("target_package") } returns pkg3.name
                            every { getString("Substratum_Target") } returns pkg4.name
                        }
                    }
                }
            } else {
                null
            }
        }

        create().process(areaInfo).apply {
            owners shouldBe setOf(
                Owner(pkg1, handle),
                Owner(pkg2, handle),
                Owner(pkg3, handle),
                Owner(pkg4, handle),
            )
            hasKnownUnknownOwner shouldBe true
        }
    }
}