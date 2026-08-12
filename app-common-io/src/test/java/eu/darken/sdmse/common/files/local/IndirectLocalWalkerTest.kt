package eu.darken.sdmse.common.files.local

import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.ReadException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
        coEvery { gateway.lookupFilesFlow(LocalPath.build("root"), mode) } returns flowOf(
            lookup("root/sub", FileType.DIRECTORY),
            lookup("root/file.txt", FileType.FILE),
        )
        coEvery { gateway.lookupFilesFlow(LocalPath.build("root/sub"), mode) } returns flowOf(
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
        coEvery { gateway.lookupFilesFlow(LocalPath.build("root"), mode) } returns flowOf(
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
        coVerify(exactly = 0) { gateway.lookupFilesFlow(LocalPath.build("root/link"), mode) }
    }

    @Test
    fun `downstream abort is not treated as a read error`() = runTest {
        val mode = LocalGateway.Mode.ROOT
        val start = LocalPath.build("root")
        coEvery { gateway.lookup(start, mode) } returns lookup("root", FileType.DIRECTORY)
        coEvery { gateway.lookupFilesFlow(LocalPath.build("root"), mode) } returns flowOf(
            lookup("root/file1.txt", FileType.FILE),
            lookup("root/file2.txt", FileType.FILE),
        )

        var onErrorCalled = false
        val firstItem = IndirectLocalWalker(
            gateway = gateway,
            mode = mode,
            start = start,
            onError = { _, _ -> onErrorCalled = true; true },
        ).first()

        firstItem.lookedUp.name shouldBe "file1.txt"
        onErrorCalled shouldBe false
    }

    @Test
    fun `mid-stream error keeps prior children and consults onError`() = runTest {
        val mode = LocalGateway.Mode.ROOT
        val start = LocalPath.build("root")
        coEvery { gateway.lookup(start, mode) } returns lookup("root", FileType.DIRECTORY)
        coEvery { gateway.lookupFilesFlow(LocalPath.build("root"), mode) } returns flow {
            emit(lookup("root/early.txt", FileType.FILE))
            emit(lookup("root/earlydir", FileType.DIRECTORY))
            throw ReadException(path = LocalPath.build("root"))
        }
        coEvery { gateway.lookupFilesFlow(LocalPath.build("root/earlydir"), mode) } returns flowOf(
            lookup("root/earlydir/nested.txt", FileType.FILE),
        )

        val consulted = mutableListOf<LocalPathLookup>()
        val items = IndirectLocalWalker(
            gateway = gateway,
            mode = mode,
            start = start,
            onError = { lookup, _ -> consulted.add(lookup); true },
        ).toList()

        // Children streamed before the error are kept, and directories among them are still walked
        items.names() shouldContainAll setOf("early.txt", "earlydir", "nested.txt")
        consulted.map { it.lookedUp.name } shouldBe listOf("root")
    }

    @Test
    fun `an onFilter exception propagates and is not consulted as read error`() = runTest {
        val mode = LocalGateway.Mode.ROOT
        val start = LocalPath.build("root")
        coEvery { gateway.lookup(start, mode) } returns lookup("root", FileType.DIRECTORY)
        coEvery { gateway.lookupFilesFlow(LocalPath.build("root"), mode) } returns flowOf(
            lookup("root/file.txt", FileType.FILE),
        )

        var onErrorCalled = false
        shouldThrow<IllegalStateException> {
            IndirectLocalWalker(
                gateway = gateway,
                mode = mode,
                start = start,
                onFilter = { throw IllegalStateException("filter boom") },
                onError = { _, _ -> onErrorCalled = true; true },
            ).toList()
        }
        onErrorCalled shouldBe false
    }

    @Test
    fun `a downstream collector exception propagates and is not consulted as read error`() = runTest {
        val mode = LocalGateway.Mode.ROOT
        val start = LocalPath.build("root")
        coEvery { gateway.lookup(start, mode) } returns lookup("root", FileType.DIRECTORY)
        coEvery { gateway.lookupFilesFlow(LocalPath.build("root"), mode) } returns flowOf(
            lookup("root/file.txt", FileType.FILE),
        )

        var onErrorCalled = false
        shouldThrow<IllegalStateException> {
            IndirectLocalWalker(
                gateway = gateway,
                mode = mode,
                start = start,
                onError = { _, _ -> onErrorCalled = true; true },
            ).collect { throw IllegalStateException("collector boom") }
        }
        onErrorCalled shouldBe false
    }

    @Test
    fun `mid-stream error aborts the walk when onError returns false`() = runTest {
        val mode = LocalGateway.Mode.ROOT
        val start = LocalPath.build("root")
        coEvery { gateway.lookup(start, mode) } returns lookup("root", FileType.DIRECTORY)
        coEvery { gateway.lookupFilesFlow(LocalPath.build("root"), mode) } returns flow {
            emit(lookup("root/early.txt", FileType.FILE))
            throw ReadException(path = LocalPath.build("root"))
        }

        shouldThrow<ReadException> {
            IndirectLocalWalker(
                gateway = gateway,
                mode = mode,
                start = start,
                onError = { _, _ -> false },
            ).toList()
        }
    }
}
