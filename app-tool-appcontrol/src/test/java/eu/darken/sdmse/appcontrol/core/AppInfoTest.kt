package eu.darken.sdmse.appcontrol.core

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.SharedLibraryInfo
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.container.LibraryPkg
import eu.darken.sdmse.common.pkgs.features.InstallDetails
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.features.InstallerInfo
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class AppInfoTest : BaseTest() {

    private val userHandle = UserHandle2(0)

    private fun appInfo(pkg: Installed) = AppInfo(
        pkg = pkg,
        isActive = null,
        sizes = null,
        usage = null,
        userProfile = null,
        canBeToggled = false,
        canBeStopped = false,
        canBeExported = false,
        canBeDeleted = false,
        canBeArchived = false,
        canBeRestored = false,
    )

    private fun libraryPkg(longVersion: Long, versionName: String? = "1"): LibraryPkg {
        val sharedLibraryInfo = mockk<SharedLibraryInfo>().apply {
            every { name } returns "com.example.lib"
            every { this@apply.longVersion } returns longVersion
            // In plain JVM tests Build.VERSION.SDK_INT == 0, so LibraryPkg.versionCode takes the
            // pre-API-28 branch reading the deprecated int `version`. Stub both, kept consistent.
            @Suppress("DEPRECATION")
            every { version } returns longVersion.toInt()
            every { type } returns SharedLibraryInfo.TYPE_DYNAMIC
        }
        val packageInfo = PackageInfo().apply {
            packageName = "com.example.lib"
            this.versionName = versionName
            applicationInfo = ApplicationInfo().apply {
                packageName = "com.example.lib"
                enabled = true
            }
        }
        return LibraryPkg(
            sharedLibraryInfo = sharedLibraryInfo,
            apkPath = mockk<APath>(relaxed = true),
            packageInfo = packageInfo,
            userHandle = userHandle,
        )
    }

    private fun normalPkg(versionName: String?, versionCode: Long): Installed {
        val pkgId = Pkg.Id("com.test.app")
        return mockk<Installed>(relaxed = true, moreInterfaces = arrayOf(InstallDetails::class)).apply {
            every { id } returns pkgId
            every { installId } returns InstallId(pkgId, userHandle)
            every { this@apply.versionName } returns versionName
            every { this@apply.versionCode } returns versionCode
            every { (this@apply as InstallDetails).installerInfo } returns InstallerInfo()
        }
    }

    @Test fun `library with sentinel version code hides the version line entirely`() {
        // Dynamic shared libraries report longVersion == -1; the whole line is dropped
        // even though versionName ("1") is non-null.
        appInfo(libraryPkg(longVersion = -1L, versionName = "1")).versionText shouldBe null
    }

    @Test fun `library with a real version code still shows the version`() {
        appInfo(libraryPkg(longVersion = 42L, versionName = "1")).versionText shouldBe "1 (42)"
    }

    @Test fun `non-library with sentinel version code is not hidden`() {
        // The hide rule is scoped to libraries; a normal app with -1 is left untouched.
        appInfo(normalPkg(versionName = "9.8", versionCode = -1L)).versionText shouldBe "9.8 (-1)"
    }

    @Test fun `normal app renders name and code`() {
        appInfo(normalPkg(versionName = "1.2.3", versionCode = 42L)).versionText shouldBe "1.2.3 (42)"
    }
}
