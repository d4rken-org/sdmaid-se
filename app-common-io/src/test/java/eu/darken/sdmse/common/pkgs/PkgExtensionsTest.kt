package eu.darken.sdmse.common.pkgs

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.SharedLibraryInfo
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.local.LocalPath
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

    private fun hiddenPkg(
        enabled: Boolean = true,
        systemFlag: Boolean = false,
        apkPath: APath? = null,
    ) = HiddenPkg(
        packageInfo = packageInfo(
            packageName = "test.hidden",
            enabled = enabled,
            flags = if (systemFlag) ApplicationInfo.FLAG_SYSTEM else 0,
        ),
        userHandle = userHandle,
        apkPath = apkPath,
    )

    private fun hiddenPkgWithoutAppInfo() = HiddenPkg(
        packageInfo = PackageInfo().apply { packageName = "test.hidden" },
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

    @Test fun `HiddenPkg reports the per-user enabled state`() {
        hiddenPkg(enabled = true).isEnabled shouldBe true
        hiddenPkg(enabled = false).isEnabled shouldBe false
    }

    @Test fun `HiddenPkg reports isSystemApp from FLAG_SYSTEM`() {
        hiddenPkg(systemFlag = true).isSystemApp shouldBe true
        hiddenPkg(systemFlag = false).isSystemApp shouldBe false
    }

    @Test fun `HiddenPkg falls back to the partition when FLAG_SYSTEM is absent`() {
        // The archive-parsed construction path has no FLAG_SYSTEM, so the APK location decides.
        hiddenPkg(
            systemFlag = false,
            apkPath = LocalPath.build("/system/priv-app/Test/Test.apk"),
        ).isSystemApp shouldBe true
        hiddenPkg(
            systemFlag = false,
            apkPath = LocalPath.build("/data/app/test.hidden-1/base.apk"),
        ).isSystemApp shouldBe false
    }

    @Test fun `HiddenPkg without applicationInfo keeps the InstallDetails defaults`() {
        // Not claiming to be disabled and not claiming to be a user app is the safe read here.
        hiddenPkgWithoutAppInfo().isEnabled shouldBe true
        hiddenPkgWithoutAppInfo().isSystemApp shouldBe true
    }

    @Test fun `HiddenPkg reports isHidden=true and isInstalled=false`() {
        hiddenPkg().isHidden shouldBe true
        hiddenPkg().isInstalled shouldBe false
    }

    @Test fun `NormalPkg reports isHidden=false and isInstalled=true`() {
        normalPkg(enabled = true).isHidden shouldBe false
        normalPkg(enabled = true).isInstalled shouldBe true
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

    @Test fun `LibraryPkg reports isLibrary=true`() {
        libraryPkg(enabled = true).isLibrary shouldBe true
    }

    @Test fun `NormalPkg reports isLibrary=false`() {
        normalPkg(enabled = true).isLibrary shouldBe false
    }

    @Test fun `ArchivedPkg reports isLibrary=false`() {
        archivedPkg().isLibrary shouldBe false
    }
}
