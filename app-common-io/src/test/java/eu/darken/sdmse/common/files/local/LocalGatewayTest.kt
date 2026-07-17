package eu.darken.sdmse.common.files.local

import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.adb.service.AdbServiceClient
import eu.darken.sdmse.common.adb.service.runModuleAction
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.WriteException
import eu.darken.sdmse.common.files.local.ipc.FileOpsClient
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.storage.StorageEnvironment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
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
        unmockkAll()
        testDir.deleteRecursively()
    }

    // Mirrors the reported bug setup: a secondary storage (SD card) whose root is writable while
    // another app's Android/data subtree is completely invisible to normal java.io.File access.
    private val storageRoot = File(testDir, "storage/1234-5678")
    private val publicData = File(storageRoot, "Android/data")
    private val hiddenTarget = File(publicData, "com.example.app/files")

    private fun CoroutineScope.gateway(
        rootAvailable: Boolean = false,
        adbAvailable: Boolean = false,
    ) = LocalGateway(
        ipcFunnel = mockk(),
        libcoreTool = mockk(),
        appScope = this,
        dispatcherProvider = TestDispatcherProvider(),
        storageEnvironment = mockk<StorageEnvironment> {
            every { externalDirs } returns listOf(LocalPath.build(storageRoot))
            every { publicDataDirs } returns listOf(LocalPath.build(publicData))
        },
        rootManager = mockk<RootManager> { every { useRoot } returns flowOf(rootAvailable) },
        adbManager = mockk<AdbManager> {
            every { useAdb } returns flowOf(adbAvailable)
            every { serviceClient } returns mockk(relaxed = true)
        },
    )

    private fun mockAdbFileOps(): FileOpsClient {
        val fileOps = mockk<FileOpsClient>()
        mockkStatic("eu.darken.sdmse.common.adb.service.AdbServiceClientExtensionsKt")
        coEvery {
            any<AdbServiceClient>().runModuleAction<FileOpsClient, Any?>(FileOpsClient::class.java, any())
        } coAnswers {
            arg<suspend (FileOpsClient) -> Any?>(2).invoke(fileOps)
        }
        return fileOps
    }

    @Test
    @Config(sdk = [30, 31])
    fun `AUTO delete escalates hidden public Android-data paths to ADB on API30+`() =
        runTest2(autoCancel = true) {
            storageRoot.mkdirs()
            val targetPath = LocalPath.build(hiddenTarget)
            val fileOps = mockAdbFileOps().apply {
                every { delete(targetPath, recursive = true, dryRun = false) } returns true
            }

            // Precondition (SM-G973F@API31 bug scenario): storage root writable, target invisible.
            storageRoot.canWrite() shouldBe true
            hiddenTarget.exists() shouldBe false

            // Old behavior: hasApiLevel(32) guard didn't fire on API 30/31, the writable storage root
            // made this a NORMAL delete, and !exists() was misread as "already gone" -> false
            // success without ever touching ADB.
            gateway(adbAvailable = true).delete(targetPath, recursive = true, mode = LocalGateway.Mode.AUTO)

            verify(exactly = 1) { fileOps.delete(targetPath, recursive = true, dryRun = false) }
        }

    @Test
    @Config(sdk = [30, 31])
    fun `AUTO delete does not fake success for hidden public Android-data paths without escalation`() =
        runTest2(autoCancel = true) {
            storageRoot.mkdirs()

            // Old behavior: silent false success ("already gone"). Without root/adb there is no way
            // to verify or perform this deletion, so it must fail instead of inflating results.
            shouldThrow<WriteException> {
                gateway().delete(LocalPath.build(hiddenTarget), recursive = true, mode = LocalGateway.Mode.AUTO)
            }
        }

    @Test
    @Config(sdk = [30, 31])
    fun `NORMAL delete of an invisible public Android-data path fails instead of faking success`() =
        runTest2(autoCancel = true) {
            storageRoot.mkdirs()

            // Explicit NORMAL forces the direct attempt: delete() returns false and raw exists() is
            // also false. Previously that combination was reported as "already gone" success.
            shouldThrow<WriteException> {
                gateway().delete(LocalPath.build(hiddenTarget), recursive = true, mode = LocalGateway.Mode.NORMAL)
            }
        }

    @Test
    fun `AUTO delete falls back to ADB when a NORMAL delete attempt fails`() =
        runTest2(autoCancel = true) {
            // Visible, writable, non-empty directory: NORMAL is chosen, but a non-recursive
            // File.delete() fails on it and the target still exists afterwards.
            val fullDir = File(storageRoot, "Documents/full").apply { mkdirs() }
            File(fullDir, "content.txt").writeText("data")
            val targetPath = LocalPath.build(fullDir)
            val fileOps = mockAdbFileOps().apply {
                every { delete(targetPath, recursive = true, dryRun = false) } returns true
            }

            // Old behavior: the ADB fallback after a failed NORMAL delete was dead code, AUTO threw
            // instead of escalating to an available ADB connection.
            gateway(adbAvailable = true).delete(targetPath, recursive = false, mode = LocalGateway.Mode.AUTO)

            verify(exactly = 1) { fileOps.delete(targetPath, recursive = true, dryRun = false) }
        }

    @Test
    @Config(sdk = [31])
    fun `AUTO delete keeps already-gone semantics outside public Android-data`() =
        runTest2(autoCancel = true) {
            storageRoot.mkdirs()
            val missing = File(storageRoot, "Documents/missing.txt")

            // Not under Android/data: exists() is trustworthy, a missing target stays a no-op success.
            gateway().delete(LocalPath.build(missing), recursive = true, mode = LocalGateway.Mode.AUTO)
        }

    @Test
    @Config(sdk = [30, 31])
    fun `AUTO exists escalates hidden public Android-data paths to ADB on API30+`() =
        runTest2(autoCancel = true) {
            storageRoot.mkdirs()
            val targetPath = LocalPath.build(hiddenTarget)
            val fileOps = mockAdbFileOps().apply {
                every { exists(targetPath) } returns true
            }

            // Old behavior: NORMAL was considered usable and reported false for content that ADB can see.
            gateway(adbAvailable = true).exists(targetPath, LocalGateway.Mode.AUTO) shouldBe true

            verify(exactly = 1) { fileOps.exists(targetPath) }
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
