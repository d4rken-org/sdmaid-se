package eu.darken.sdmse.common.pkgs

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.SharedLibraryInfo
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.pkgs.container.ArchivedPkg
import eu.darken.sdmse.common.pkgs.container.HiddenPkg
import eu.darken.sdmse.common.pkgs.container.LibraryPkg
import eu.darken.sdmse.common.pkgs.container.NormalPkg
import eu.darken.sdmse.common.pkgs.features.InstallerInfo
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class PkgExtensionsTest : BaseTest() {

    private val userHandle = UserHandle2(0)

    private fun packageInfo(
        packageName: String = "test.pkg",
        enabled: Boolean = true,
        flags: Int = 0,
        firstInstallTime: Long = 0L,
    ): PackageInfo {
        val appInfo = ApplicationInfo().apply {
            this.packageName = packageName
            this.enabled = enabled
            this.flags = flags
        }
        return PackageInfo().apply {
            this.packageName = packageName
            this.applicationInfo = appInfo
            this.firstInstallTime = firstInstallTime
        }
    }

    private fun normalPkg(enabled: Boolean, systemFlag: Boolean = false) = NormalPkg(
        packageInfo = packageInfo(
            packageName = "test.normal",
            enabled = enabled,
            flags = if (systemFlag) ApplicationInfo.FLAG_SYSTEM else 0,
        ),
        installerInfo = InstallerInfo(),
        userHandle = userHandle,
    )

    private fun libraryPkg(enabled: Boolean): LibraryPkg {
        val sharedLibraryInfo = mockk<SharedLibraryInfo>().apply {
            every { name } returns "com.example.lib"
            every { longVersion } returns 42L
            every { type } returns SharedLibraryInfo.TYPE_STATIC
        }
        val apkPath = mockk<APath>(relaxed = true)
        return LibraryPkg(
            sharedLibraryInfo = sharedLibraryInfo,
            apkPath = apkPath,
            packageInfo = packageInfo(
                packageName = "com.example.lib_42",
                enabled = enabled,
                flags = 0, // APK-parsed PackageInfo has no FLAG_SYSTEM
            ),
            userHandle = userHandle,
        )
    }

    private fun hiddenPkg() = HiddenPkg(
        packageInfo = packageInfo(packageName = "test.hidden"),
        userHandle = userHandle,
    )

    private fun archivedPkg() = ArchivedPkg(
        packageInfo = packageInfo(packageName = "test.archived"),
        userHandle = userHandle,
        installerInfo = InstallerInfo(),
    )

    @Test fun `NormalPkg with enabled applicationInfo reports isEnabled=true`() {
        normalPkg(enabled = true).isEnabled shouldBe true
    }

    @Test fun `NormalPkg with disabled applicationInfo reports isEnabled=false`() {
        normalPkg(enabled = false).isEnabled shouldBe false
    }

    @Test fun `LibraryPkg with enabled applicationInfo reports isEnabled=true`() {
        // Regression test for #2357: static shared libraries used to always
        // report isEnabled=false because LibraryPkg did not implement InstallDetails.
        libraryPkg(enabled = true).isEnabled shouldBe true
    }

    @Test fun `LibraryPkg with disabled applicationInfo reports isEnabled=false`() {
        // In production this state is only reachable via a live root/ADB PM query
        // (MATCH_STATIC_SHARED_AND_SDK_LIBRARIES). The APK-parsed fallback from
        // getPackageArchiveInfo() returns manifest-default enabled=true.
        libraryPkg(enabled = false).isEnabled shouldBe false
    }

    @Test fun `HiddenPkg is not InstallDetails and reports isEnabled=false`() {
        // HiddenPkg intentionally does not implement InstallDetails, so the
        // extension short-circuits to false. Preserves the pre-existing behavior.
        hiddenPkg().isEnabled shouldBe false
    }

    @Test fun `ArchivedPkg hardcodes isEnabled=false`() {
        archivedPkg().isEnabled shouldBe false
    }

    @Test fun `NormalPkg without FLAG_SYSTEM reports isSystemApp=false`() {
        normalPkg(enabled = true, systemFlag = false).isSystemApp shouldBe false
    }

    @Test fun `NormalPkg with FLAG_SYSTEM reports isSystemApp=true`() {
        normalPkg(enabled = true, systemFlag = true).isSystemApp shouldBe true
    }

    @Test fun `LibraryPkg always reports isSystemApp=true regardless of flags`() {
        // LibraryPkg overrides isSystemApp to always return true, because
        // getPackageArchiveInfo does not populate FLAG_SYSTEM for APK-parsed
        // PackageInfo. The extension now reaches this via InstallDetails.
        libraryPkg(enabled = true).isSystemApp shouldBe true
    }
}
