package eu.darken.sdmse.common.files.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.darken.sdmse.common.files.FileType
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import testhelper.EmptyApp
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import java.io.File
import java.nio.file.Files

@RunWith(AndroidJUnit4::class)
@Config(sdk = [29], application = EmptyApp::class)
class LocalGatewayTest {

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
                mockk(),
                mockk(),
                this,
                TestDispatcherProvider(),
                mockk(),
                mockk(relaxed = true),
                mockk(relaxed = true),
            )

            val link = File(testDir, "broken-link")
            Files.createSymbolicLink(link.toPath(), File(testDir, "missing-target").toPath())
            val linkPath = LocalPath.build(link)

            // Precondition: the link is unreadable via canRead() because it follows the dead target.
            link.canRead() shouldBe false

            gateway.lookup(linkPath, LocalGateway.Mode.NORMAL).fileType shouldBe FileType.SYMBOLIC_LINK
            gateway.lookup(linkPath, LocalGateway.Mode.AUTO).fileType shouldBe FileType.SYMBOLIC_LINK
        }
}
