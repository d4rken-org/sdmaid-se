package eu.darken.sdmse.common.pkgs.pkgops

import android.content.IntentSender
import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.adb.service.AdbServiceClient
import eu.darken.sdmse.common.adb.service.runModuleAction
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.pkgops.ipc.PkgOpsClient
import eu.darken.sdmse.common.pkgs.pkgops.ipc.PkgOpsConnection
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.service.RootServiceClient
import eu.darken.sdmse.common.root.service.runModuleAction
import eu.darken.sdmse.common.user.UserHandle2
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2

class PkgOpsRequestUnarchiveTest : BaseTest() {

    private val otherUserInstall = InstallId("eu.thlab.target".toPkgId(), UserHandle2(handleId = 10))
    private val statusReceiver = mockk<IntentSender>()

    private val connection = mockk<PkgOpsConnection>().apply {
        every { requestUnarchive(any(), any(), any()) } just Runs
    }
    private val client = PkgOpsClient(connection)

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    private fun create(scope: CoroutineScope, useRoot: Boolean, useAdb: Boolean): PkgOps {
        mockkStatic("eu.darken.sdmse.common.root.service.RootServiceClientExtensionsKt")
        coEvery {
            any<RootServiceClient>().runModuleAction<PkgOpsClient, Any?>(PkgOpsClient::class.java, any())
        } coAnswers {
            arg<suspend (PkgOpsClient) -> Any?>(2).invoke(client)
        }
        mockkStatic("eu.darken.sdmse.common.adb.service.AdbServiceClientExtensionsKt")
        coEvery {
            any<AdbServiceClient>().runModuleAction<PkgOpsClient, Any?>(PkgOpsClient::class.java, any())
        } coAnswers {
            arg<suspend (PkgOpsClient) -> Any?>(2).invoke(client)
        }

        return PkgOps(
            appScope = scope,
            dispatcherProvider = TestDispatcherProvider(),
            context = mockk(),
            ipcFunnel = mockk(),
            rootManager = mockk<RootManager> {
                every { this@mockk.useRoot } returns flowOf(useRoot)
                every { serviceClient } returns mockk(relaxed = true)
            },
            adbManager = mockk<AdbManager> {
                every { this@mockk.useAdb } returns flowOf(useAdb)
                every { serviceClient } returns mockk(relaxed = true)
            },
            usageStatsManager = mockk(),
            storageStatsManager = mockk(),
            userManager2 = mockk(),
        )
    }

    @Test
    fun `root unarchive request carries the package name and user handle`() = runTest2 {
        create(this, useRoot = true, useAdb = false).requestUnarchive(otherUserInstall, statusReceiver)

        verify(exactly = 1) { connection.requestUnarchive("eu.thlab.target", 10, statusReceiver) }
    }

    @Test
    fun `adb unarchive request carries the package name and user handle`() = runTest2 {
        create(this, useRoot = false, useAdb = true).requestUnarchive(otherUserInstall, statusReceiver)

        verify(exactly = 1) { connection.requestUnarchive("eu.thlab.target", 10, statusReceiver) }
    }
}
