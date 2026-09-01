package eu.darken.sdmse.common.pkgs.pkgops.ipc

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import eu.darken.flowshell.core.cmd.FlowCmd
import eu.darken.flowshell.core.process.FlowProcess
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.pkgops.ProcessScanner
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.shell.SharedShell
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.assertions.throwables.shouldThrow
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication

/**
 * Robolectric is mandatory: [PkgOpsHost] extends the generated AIDL Binder stub.
 *
 * Only the secondary-user branch of [PkgOpsHost.setApplicationEnabledSetting] is covered: the
 * current-user branch goes straight to `PackageManager.setApplicationEnabledSetting`, which needs
 * privileges Robolectric does not model.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class PkgOpsHostTest : BaseTest() {

    private val otherUser = InstallId("eu.thlab.test".toPkgId(), UserHandle2(handleId = 1))

    @After
    fun cleanup() {
        unmockkAll()
    }

    private fun host(exitCode: FlowProcess.ExitCode): PkgOpsHost {
        val sharedShell = mockk<SharedShell>().apply {
            coEvery { useRes<FlowCmd.Result>(any()) } returns FlowCmd.Result(
                original = FlowCmd("pm enable"),
                exitCode = exitCode,
                output = emptyList(),
                errors = listOf("Failure"),
            )
        }
        return PkgOpsHost(
            context = ApplicationProvider.getApplicationContext<Context>(),
            sharedShell = sharedShell,
            processScanner = mockk<ProcessScanner>(),
        )
    }

    @Test
    fun `a successful pm call on a secondary user returns normally`() {
        host(FlowProcess.ExitCode.OK).setApplicationEnabledSetting(
            id = otherUser,
            newState = PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            flags = 0,
        )
    }

    @Test
    fun `a failing pm call on a secondary user throws`() {
        // Without this the caller records the toggle as successful purely because nothing threw.
        shouldThrow<UnsupportedOperationException> {
            host(FlowProcess.ExitCode(1)).setApplicationEnabledSetting(
                id = otherUser,
                newState = PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                flags = 0,
            )
        }
    }
}
