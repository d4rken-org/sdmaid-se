package eu.darken.sdmse.common.files.local

import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.root.RootManager
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelper.EmptyApp
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = EmptyApp::class)
class LocalGatewayTest : BaseTest() {

    private val testDir = Files.createTempDirectory("localgateway-test").toFile()

    @After
    fun cleanup() {
        testDir.deleteRecursively()
    }

    @Test
    fun `lookup of a broken symlink resolves app-side without privileged escalation`() =
        runTest2(autoCancel = true) {
            // rootManager/adbManager are mocked away (no privileged access), so a lookup that
            // succeeds and classifies the link can only have gone through the app-side NOFOLLOW
            // path. On the old code canRead() follows the dead target -> false -> NORMAL throws and
            // AUTO has nowhere to escalate.
            val gateway = LocalGateway(
                ipcFunnel = mockk(),
                libcoreTool = mockk(),
                appScope = this,
                dispatcherProvider = TestDispatcherProvider(),
                storageEnvironment = mockk(),
                rootManager = mockk(relaxed = true),
                adbManager = mockk(relaxed = true),
            )

            val link = File(testDir, "broken-link")
            Files.createSymbolicLink(link.toPath(), File(testDir, "missing-target").toPath())
            val linkPath = LocalPath.build(link)

            // Precondition: the link is unreadable via canRead() because it follows the dead target.
            link.canRead() shouldBe false

            gateway.lookup(linkPath, LocalGateway.Mode.NORMAL).fileType shouldBe FileType.SYMBOLIC_LINK
            gateway.lookup(linkPath, LocalGateway.Mode.AUTO).fileType shouldBe FileType.SYMBOLIC_LINK
        }

    @Test
    fun `AUTO resolves a broken symlink app-side even when root is available`() =
        runTest2(autoCancel = true) {
            // canUseRootNow() is an extension over RootManager.useRoot, so stub the flow, not the call.
            // The root mock is non-relaxed: if the broken symlink were (wrongly) escalated, rootOps
            // would touch unstubbed members and throw, so a SYMBOLIC_LINK result proves app-side lstat.
            val rootManager = mockk<RootManager> { every { useRoot } returns flowOf(true) }
            val gateway = LocalGateway(
                ipcFunnel = mockk(),
                libcoreTool = mockk(),
                appScope = this,
                dispatcherProvider = TestDispatcherProvider(),
                storageEnvironment = mockk(),
                rootManager = rootManager,
                adbManager = mockk(relaxed = true),
            )

            val link = File(testDir, "broken-link-rooted")
            Files.createSymbolicLink(link.toPath(), File(testDir, "missing-target").toPath())

            gateway.hasRoot() shouldBe true // precondition: escalation WAS available
            gateway.lookup(LocalPath.build(link), LocalGateway.Mode.AUTO).fileType shouldBe FileType.SYMBOLIC_LINK
        }
}
