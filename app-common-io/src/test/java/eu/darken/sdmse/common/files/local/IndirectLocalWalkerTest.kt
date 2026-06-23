package eu.darken.sdmse.common.files.local

import eu.darken.sdmse.common.files.FileType
import io.kotest.matchers.collections.shouldContainAll
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class IndirectLocalWalkerTest : BaseTest() {

    private val gateway = mockk<LocalGateway>()

    private fun lookup(
        path: String,
        fileType: FileType,
        target: LocalPath? = null,
    ) = LocalPathLookup(
        lookedUp = LocalPath.build(path),
        fileType = fileType,
        size = 4096L,
        modifiedAt = Instant.EPOCH,
        target = target,
    )

    private fun List<LocalPathLookup>.names(): Set<String> = map { it.lookedUp.name }.toSet()

    @Test
    fun `descends into real directories`() = runTest {
        val mode = LocalGateway.Mode.ROOT
        val start = LocalPath.build("root")
        coEvery { gateway.lookup(start, mode) } returns lookup("root", FileType.DIRECTORY)
        coEvery { gateway.lookupFiles(LocalPath.build("root"), mode) } returns listOf(
            lookup("root/sub", FileType.DIRECTORY),
            lookup("root/file.txt", FileType.FILE),
        )
        coEvery { gateway.lookupFiles(LocalPath.build("root/sub"), mode) } returns listOf(
            lookup("root/sub/nested.txt", FileType.FILE),
        )

        val items = IndirectLocalWalker(gateway = gateway, mode = mode, start = start).toList()

        items.names() shouldContainAll setOf("sub", "file.txt", "nested.txt")
    }

    @Test
    fun `followSymlinks does not descend into symlinked directories in indirect mode`() = runTest {
        // In ROOT/ADB the app process can't stat the symlink target, so this walker intentionally
        // never follows symlinks (callback-free follow-walks go host-side instead). The symlink is
        // still emitted, but must NOT be descended into.
        val mode = LocalGateway.Mode.ROOT
        val start = LocalPath.build("root")
        coEvery { gateway.lookup(start, mode) } returns lookup("root", FileType.DIRECTORY)
        coEvery { gateway.lookupFiles(LocalPath.build("root"), mode) } returns listOf(
            lookup("root/link", FileType.SYMBOLIC_LINK, target = LocalPath.build("elsewhere")),
        )

        val items = IndirectLocalWalker(
            gateway = gateway,
            mode = mode,
            start = start,
            followSymlinks = true,
        ).toList()

        items.names() shouldContainAll setOf("link")
        // The symlink target must never be listed through the gateway (no descent).
        coVerify(exactly = 0) { gateway.lookupFiles(LocalPath.build("root/link"), mode) }
    }
}
