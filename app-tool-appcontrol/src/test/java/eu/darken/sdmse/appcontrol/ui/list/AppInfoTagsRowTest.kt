package eu.darken.sdmse.appcontrol.ui.list

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.SharedLibraryInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.container.LibraryPkg
import eu.darken.sdmse.common.pkgs.features.InstallDetails
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.features.InstallerInfo
import eu.darken.sdmse.common.user.UserHandle2
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class AppInfoTagsRowTest : BaseComposeRobolectricTest() {

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

    private fun libraryPkg(): LibraryPkg {
        val sharedLibraryInfo = mockk<SharedLibraryInfo>().apply {
            every { name } returns "android.ext.shared"
            every { longVersion } returns -1L
            every { type } returns SharedLibraryInfo.TYPE_DYNAMIC
        }
        val packageInfo = PackageInfo().apply {
            packageName = "com.google.android.ext.shared"
            applicationInfo = ApplicationInfo().apply {
                packageName = "com.google.android.ext.shared"
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

    private fun systemPkg(): Installed {
        val pkgId = Pkg.Id("com.system.app")
        return mockk<Installed>(relaxed = true, moreInterfaces = arrayOf(InstallDetails::class)).apply {
            every { id } returns pkgId
            every { installId } returns InstallId(pkgId, userHandle)
            every { packageName } returns "com.system.app"
            every { (this@apply as InstallDetails).isEnabled } returns true
            every { (this@apply as InstallDetails).isSystemApp } returns true
            every { (this@apply as InstallDetails).isDebuggable } returns false
            every { (this@apply as InstallDetails).installerInfo } returns InstallerInfo()
        }
    }

    @Test
    fun `library entry renders the Library tag and not the System tag`() {
        composeRule.setContent {
            PreviewWrapper {
                AppInfoTagsRow(appInfo = appInfo(libraryPkg()))
            }
        }

        composeRule.onNodeWithText("Library").assertExists()
        composeRule.onAllNodesWithText("System").assertCountEquals(0)
    }

    @Test
    fun `normal system app renders the System tag and not the Library tag`() {
        composeRule.setContent {
            PreviewWrapper {
                AppInfoTagsRow(appInfo = appInfo(systemPkg()))
            }
        }

        composeRule.onNodeWithText("System").assertExists()
        composeRule.onAllNodesWithText("Library").assertCountEquals(0)
    }
}
