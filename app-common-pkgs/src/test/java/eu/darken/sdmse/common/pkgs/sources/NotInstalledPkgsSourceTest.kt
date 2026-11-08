package eu.darken.sdmse.common.pkgs.sources

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.ArchivedPackageInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import eu.darken.sdmse.common.BuildWrap
import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.permissions.Permission
import eu.darken.sdmse.common.pkgs.container.ArchivedPkg
import eu.darken.sdmse.common.pkgs.container.HiddenPkg
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.common.user.UserManager2
import eu.darken.sdmse.common.user.UserProfile2
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class NotInstalledPkgsSourceTest : BaseTest() {

    private val owner = UserHandle2(handleId = 0)
    private val secondary = UserHandle2(handleId = 10)
    private val pkgName = "eu.thlab.target"

    // The APK stays on disk while any user still has the package installed.
    private val apkPath = "/data/app/~~abc==/eu.thlab.target-xyz==/base.apk"

    @BeforeEach
    fun setup() {
        mockkObject(BuildWrap)
        mockkObject(BuildWrap.VERSION)
        every { BuildWrap.VERSION.SDK_INT } returns 35
        every { BuildWrap.VERSION.CODENAME } returns "REL"

        mockkObject(Permission.QUERY_ALL_PACKAGES)
        every { Permission.QUERY_ALL_PACKAGES.isGranted(any()) } returns true
    }

    @AfterEach
    fun teardown() {
        unmockkObject(Permission.QUERY_ALL_PACKAGES)
        unmockkObject(BuildWrap.VERSION)
        unmockkObject(BuildWrap)
    }

    /** Relaxed for the same reason as in ShellLookUpPkgsSourceTest: `PackageInfo.toString()` is not mocked. */
    private fun packageInfo(archiveTime: Long) = mockk<PackageInfo>(relaxed = true).apply {
        packageName = pkgName
        applicationInfo = ApplicationInfo().apply {
            packageName = pkgName
            sourceDir = apkPath
            flags = 0 // FLAG_INSTALLED unset: not installed for this user
        }
        every { archiveTimeMillis } returns archiveTime
    }

    private fun create(secondaryUserPkgs: Collection<PackageInfo>): NotInstalledPkgsSource {
        val pkgOps = mockk<PkgOps>().apply {
            // A relaxed mock would swallow the lambda, and with it the entire body of getPkgs().
            coEvery { useRes<Collection<Installed>>(any()) } coAnswers {
                firstArg<suspend (Any) -> Collection<Installed>>().invoke(Unit)
            }
            // Owner core list and every flags=0 ("normal apps") lookup come back empty.
            coEvery { queryPkgs(any(), any(), any()) } returns emptySet()
            coEvery { queryPkgs(neq(0L), eq(secondary), any()) } returns secondaryUserPkgs
            coEvery { getInstallerData(any()) } returns emptyMap()
        }
        val pm = mockk<PackageManager>().apply {
            // Non-null for any archivable app, also while it is still installed in the calling user.
            every { getArchivedPackage(pkgName) } returns mockk<ArchivedPackageInfo>(relaxed = true)
        }
        val context = mockk<Context>().apply {
            every { packageManager } returns pm
        }
        val rootManager = mockk<RootManager>().apply {
            every { useRoot } returns flowOf(true)
        }
        val adbManager = mockk<AdbManager>().apply {
            every { useAdb } returns flowOf(false)
        }
        val userManager = mockk<UserManager2>().apply {
            coEvery { currentUser() } returns UserProfile2(handle = owner)
            coEvery { otherUsers() } returns setOf(UserProfile2(handle = secondary))
        }
        return NotInstalledPkgsSource(
            context = context,
            pkgOps = pkgOps,
            rootManager = rootManager,
            adbManager = adbManager,
            userManager = userManager,
        )
    }

    @Test
    fun `a package archived in another user while installed in the owner user is an ArchivedPkg`() = runTest {
        val result = create(listOf(packageInfo(archiveTime = 1_794_120_685_540L))).getPkgs()

        result.singleOrNull { it.userHandle == secondary }
            .shouldNotBeNull()
            .shouldBeInstanceOf<ArchivedPkg>()
    }

    @Test
    fun `a package with an apk and no archive time is not an ArchivedPkg`() = runTest {
        val result = create(listOf(packageInfo(archiveTime = 0L))).getPkgs()

        result.singleOrNull { it.userHandle == secondary }
            .shouldNotBeNull()
            .shouldBeInstanceOf<HiddenPkg>()
    }
}
