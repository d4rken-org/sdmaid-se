package eu.darken.sdmse.appcontrol.core

import eu.darken.sdmse.appcontrol.core.archive.ArchiveTask
import eu.darken.sdmse.appcontrol.core.export.AppExportTask
import eu.darken.sdmse.appcontrol.core.export.AppExporter
import eu.darken.sdmse.appcontrol.core.forcestop.ForceStopTask
import eu.darken.sdmse.appcontrol.core.restore.RestoreTask
import eu.darken.sdmse.appcontrol.core.uninstall.UninstallTask
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

/**
 * The list screen's snackbar and the action sheet both render `primaryInfo` alone, so a failure
 * that only lands in `secondaryInfo` is invisible there.
 */
class AppControlResultTextTest : BaseTest() {

    private val testHandle = UserHandle2(0)
    private val installId1 = InstallId(Pkg.Id("com.test.app1"), testHandle)
    private val installId2 = InstallId(Pkg.Id("com.test.app2"), testHandle)

    private fun exportResult(installId: InstallId) = AppExporter.Result(
        installId = installId,
        baseApk = null,
        extraSources = null,
        savePath = mockk(),
        exportSize = 1L,
    )

    @Test
    fun `force stop primaryInfo carries the failure count`() {
        ForceStopTask.Result(
            success = setOf(installId1),
            failed = emptySet(),
        ).primaryInfo.resolve() shouldBe "1 stopped"

        ForceStopTask.Result(
            success = setOf(installId1),
            failed = setOf(installId2),
        ).primaryInfo.resolve() shouldBe "1 stopped, 1 failed"

        ForceStopTask.Result(
            success = emptySet(),
            failed = setOf(installId1, installId2),
        ).primaryInfo.resolve() shouldBe "2 failed"
    }

    @Test
    fun `archive primaryInfo carries the failure count`() {
        ArchiveTask.Result(
            success = setOf(installId1),
            failed = emptySet(),
        ).primaryInfo.resolve() shouldBe "1 archived"

        ArchiveTask.Result(
            success = setOf(installId1),
            failed = setOf(installId2),
        ).primaryInfo.resolve() shouldBe "1 archived, 1 failed"

        ArchiveTask.Result(
            success = emptySet(),
            failed = setOf(installId1, installId2),
        ).primaryInfo.resolve() shouldBe "2 failed"
    }

    @Test
    fun `restore primaryInfo carries the failure count`() {
        RestoreTask.Result(
            success = setOf(installId1),
            failed = emptySet(),
        ).primaryInfo.resolve() shouldBe "1 restored"

        RestoreTask.Result(
            success = setOf(installId1),
            failed = setOf(installId2),
        ).primaryInfo.resolve() shouldBe "1 restored, 1 failed"

        RestoreTask.Result(
            success = emptySet(),
            failed = setOf(installId1, installId2),
        ).primaryInfo.resolve() shouldBe "2 failed"
    }

    @Test
    fun `uninstall primaryInfo carries the failure count`() {
        UninstallTask.Result(
            success = setOf(installId1),
            failed = emptySet(),
        ).primaryInfo.resolve() shouldBe "1 uninstalled"

        UninstallTask.Result(
            success = setOf(installId1),
            failed = setOf(installId2),
        ).primaryInfo.resolve() shouldBe "1 uninstalled, 1 failed"

        UninstallTask.Result(
            success = emptySet(),
            failed = setOf(installId1, installId2),
        ).primaryInfo.resolve() shouldBe "2 failed"
    }

    @Test
    fun `export primaryInfo carries the failure count`() {
        AppExportTask.Result(
            success = setOf(exportResult(installId1)),
            failed = emptySet(),
        ).primaryInfo.resolve() shouldBe "1 exported"

        AppExportTask.Result(
            success = setOf(exportResult(installId1)),
            failed = setOf(installId2),
        ).primaryInfo.resolve() shouldBe "1 exported, 1 failed"

        AppExportTask.Result(
            success = emptySet(),
            failed = setOf(installId1, installId2),
        ).primaryInfo.resolve() shouldBe "2 failed"
    }
}
