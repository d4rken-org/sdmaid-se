package eu.darken.sdmse.common.pkgs.sources

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.pkgs.container.HiddenPkg
import eu.darken.sdmse.common.pkgs.container.PkgArchive
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.shell.ShellOps
import eu.darken.sdmse.common.shell.ipc.ShellOpsStreamEvent
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.common.user.UserManager2
import eu.darken.sdmse.common.user.UserProfile2
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class ShellLookUpPkgsSourceTest : BaseTest() {

    private val userHandle = UserHandle2(handleId = 0)
    private val pkgName = "com.example.foo"
    private val apkPath = "/product/app/Foo/Foo.apk"

    /**
     * A relaxed mock, not a bare `PackageInfo()`: the source logs `"...: $stateBacked"`, and
     * `PackageInfo.toString()` throws "not mocked" against the stub android.jar. Fields are read
     * off the instance directly, so assigning them still works.
     */
    private fun packageInfo(name: String) = mockk<PackageInfo>(relaxed = true).apply {
        packageName = name
        applicationInfo = ApplicationInfo().apply { this.packageName = name }
    }

    private fun create(
        queryPkgAnswer: suspend () -> PackageInfo?,
    ): ShellLookUpPkgsSource {
        val pkgOps = mockk<PkgOps>().apply {
            // A relaxed mock would swallow the lambda, and with it the entire body of getPkgs().
            coEvery { useRes<Collection<Installed>>(any()) } coAnswers {
                firstArg<suspend (Any) -> Collection<Installed>>().invoke(Unit)
            }
            coEvery { queryPkgs(any(), any(), any()) } returns emptySet()
            coEvery { queryPkg(any(), any(), any(), any()) } coAnswers { queryPkgAnswer() }
            coEvery { viewArchive(any(), any()) } returns PkgArchive(
                id = pkgName.toPkgId(),
                packageInfo = packageInfo(pkgName),
            )
        }
        val rootManager = mockk<RootManager>().apply {
            every { useRoot } returns flowOf(true)
        }
        val adbManager = mockk<AdbManager>().apply {
            every { useAdb } returns flowOf(false)
        }
        val userManager = mockk<UserManager2>().apply {
            coEvery { allUsers() } returns setOf(UserProfile2(handle = userHandle))
        }
        val shellOps = mockk<ShellOps>().apply {
            every { executeStream(any(), any()) } returns flowOf(
                ShellOpsStreamEvent.Stdout("package:$apkPath=$pkgName"),
                ShellOpsStreamEvent.Exit(0),
            )
        }
        return ShellLookUpPkgsSource(
            pkgOps = pkgOps,
            rootManager = rootManager,
            adbManager = adbManager,
            userManager = userManager,
            shellOps = shellOps,
        )
    }

    @Test
    fun `a successful state backed lookup supplies the package info`() = runTest {
        val result = create(queryPkgAnswer = { packageInfo(pkgName) }).getPkgs()

        result.size shouldBe 1
        result.single().shouldBeInstanceOf<HiddenPkg>().id shouldBe pkgName.toPkgId()
    }

    @Test
    fun `a failing state backed lookup falls back to the archive instead of aborting the scan`() = runTest {
        // Every other per-entry step in getPkgs() degrades gracefully (bad exit code skips the user,
        // a null archive or a package-name mismatch skips the entry). queryPkg is the one unguarded
        // call, so a single throwing package must not take the whole scan with it.
        val result = create(
            queryPkgAnswer = { throw IllegalStateException("pm query blew up") },
        ).getPkgs()

        result.size shouldBe 1
        val hidden = result.single().shouldBeInstanceOf<HiddenPkg>()
        hidden.id shouldBe pkgName.toPkgId()
        hidden.apkPath?.path shouldBe apkPath
    }
}
