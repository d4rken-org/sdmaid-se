package eu.darken.sdmse.common.pkgs

import android.content.pm.ApplicationInfo
import eu.darken.sdmse.common.pkgs.container.NormalPkg
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class PkgRepoExtensionsTest : BaseTest() {

    private fun normalPkg(
        pkgId: String,
        uid: Int,
        userHandle: UserHandle2 = UserHandle2(0),
    ): NormalPkg = mockk<NormalPkg>().apply {
        every { id } returns pkgId.toPkgId()
        every { this@apply.userHandle } returns userHandle
        every { applicationInfo } returns mockk<ApplicationInfo>(relaxed = true).apply { this.uid = uid }
    }

    private fun repoOf(vararg pkgs: Installed): PkgRepo = mockk<PkgRepo>().apply {
        every { data } returns flowOf(PkgRepo.PkgData.from(pkgs.toSet()))
    }

    @Test fun `resolves a per-user app uid to its package`() = runTest {
        val repo = repoOf(normalPkg("com.app.one", uid = 10123))
        repo.getPkgsForUid(10123L).map { it.id } shouldContainExactlyInAnyOrder listOf("com.app.one".toPkgId())
    }

    @Test fun `shared uid returns all sharing packages`() = runTest {
        val repo = repoOf(
            normalPkg("android", uid = 1000),
            normalPkg("com.samsung.android.wifi.ai", uid = 1000),
            normalPkg("com.unrelated", uid = 10500),
        )
        repo.getPkgsForUid(1000L).map { it.id } shouldContainExactlyInAnyOrder
            listOf("android".toPkgId(), "com.samsung.android.wifi.ai".toPkgId())
    }

    @Test fun `decomposes a secondary-user uid - matches only that user`() = runTest {
        // uid 1012345 => userId 10, appId 12345
        val repo = repoOf(
            normalPkg("com.app", uid = 12345, userHandle = UserHandle2(0)),
            normalPkg("com.app", uid = 1012345, userHandle = UserHandle2(10)),
        )
        repo.getPkgsForUid(1012345L).map { it.userHandle } shouldContainExactlyInAnyOrder listOf(UserHandle2(10))
    }

    @Test fun `uid of zero or below resolves to nothing`() = runTest {
        val repo = repoOf(normalPkg("android", uid = 0))
        repo.getPkgsForUid(0L) shouldBe emptySet()
        repo.getPkgsForUid(-1L) shouldBe emptySet()
    }

    @Test fun `packages without applicationInfo are ignored`() = runTest {
        val repo = mockk<PkgRepo>().apply {
            val pkg = mockk<NormalPkg>().apply {
                every { id } returns "com.noappinfo".toPkgId()
                every { userHandle } returns UserHandle2(0)
                every { applicationInfo } returns null
            }
            every { data } returns flowOf(PkgRepo.PkgData.from(setOf(pkg)))
        }
        repo.getPkgsForUid(10042L) shouldBe emptySet()
    }

    @Test fun `non-NormalPkg entries are ignored`() = runTest {
        // e.g. an archived/uninstalled cache entry with a stale uid must not mask a corpse
        val stale = mockk<Installed>().apply {
            every { id } returns "com.stale".toPkgId()
            every { userHandle } returns UserHandle2(0)
            every { applicationInfo } returns mockk<ApplicationInfo>(relaxed = true).apply { this.uid = 10777 }
        }
        val repo = mockk<PkgRepo>().apply {
            every { data } returns flowOf(PkgRepo.PkgData.from(setOf(stale)))
        }
        repo.getPkgsForUid(10777L) shouldBe emptySet()
    }
}
