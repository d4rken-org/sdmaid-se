package eu.darken.sdmse.appcontrol.core.archive

import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.stats.core.AffectedPkg
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class ArchiveTaskTest : BaseTest() {

    private val testHandle = UserHandle2(0)
    private val testPkgId1 = Pkg.Id("com.test.app1")
    private val testPkgId2 = Pkg.Id("com.test.app2")
    private val installId1 = InstallId(testPkgId1, testHandle)
    private val installId2 = InstallId(testPkgId2, testHandle)

    @Test
    fun `task holds targets correctly`() {
        val task = ArchiveTask(targets = setOf(installId1, installId2))

        task.targets shouldContainExactly setOf(installId1, installId2)
    }

    @Test
    fun `result reports success correctly`() {
        val result = ArchiveTask.Result(
            success = setOf(installId1, installId2),
            failed = emptySet(),
        )

        result.success shouldContainExactly setOf(installId1, installId2)
        result.failed shouldBe emptySet()
    }

    @Test
    fun `result reports failures correctly`() {
        val result = ArchiveTask.Result(
            success = setOf(installId1),
            failed = setOf(installId2),
        )

        result.success shouldContainExactly setOf(installId1)
        result.failed shouldContainExactly setOf(installId2)
    }

    @Test
    fun `result provides affected pkgs with ARCHIVED action`() {
        val result = ArchiveTask.Result(
            success = setOf(installId1, installId2),
            failed = emptySet(),
        )

        result.affectedPkgs[testPkgId1] shouldBe AffectedPkg.Action.ARCHIVED
        result.affectedPkgs[testPkgId2] shouldBe AffectedPkg.Action.ARCHIVED
    }

    @Test
    fun `result provides primary info`() {
        val result = ArchiveTask.Result(
            success = setOf(installId1),
            failed = emptySet(),
        )

        result.primaryInfo shouldNotBe null
    }

    @Test
    fun `result provides secondary info when there are failures`() {
        val result = ArchiveTask.Result(
            success = setOf(installId1),
            failed = setOf(installId2),
        )

        result.secondaryInfo shouldNotBe null
    }

    @Test
    fun `result secondary info is null when no failures`() {
        val result = ArchiveTask.Result(
            success = setOf(installId1),
            failed = emptySet(),
        )

        result.secondaryInfo shouldBe null
    }
}
