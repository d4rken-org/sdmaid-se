package eu.darken.sdmse.appcleaner.core.forensics

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.Segments
import eu.darken.sdmse.common.files.WriteException
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.pkgs.Pkg
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

/**
 * Covers [BaseExpendablesFilter.deleteAll], the shared deletion path of every AppCleaner filter.
 *
 * Matched directories are deleted as directories: over SAF that is the only thing that removes them
 * at all, everything else just empties them.
 */
class BaseExpendablesFilterTest : BaseTest() {

    private val gatewaySwitch = mockk<GatewaySwitch>()

    private class TestFilter : BaseExpendablesFilter() {
        override suspend fun initialize() {}

        override suspend fun match(
            pkgId: Pkg.Id,
            target: APathLookup<APath>,
            areaType: DataArea.Type,
            pfpSegs: Segments,
        ): ExpendablesFilter.Match? = null

        override suspend fun process(
            targets: Collection<ExpendablesFilter.Match>,
            allMatches: Collection<ExpendablesFilter.Match>,
        ): ExpendablesFilter.ProcessResult = throw NotImplementedError()
    }

    private fun match(path: String, fileType: FileType = FileType.DIRECTORY): ExpendablesFilter.Match.Deletion =
        ExpendablesFilter.Match.Deletion(
            identifier = TestFilter::class,
            lookup = LocalPathLookup(
                lookedUp = LocalPath.build(path),
                fileType = fileType,
                size = 16L,
                modifiedAt = Instant.EPOCH,
                target = null,
            ),
        )

    @Test
    fun `a matched directory is deleted recursively`() = runTest {
        val target = match("/storage/emulated/0/Android/data/com.test.pkg/cache/dir")
        coEvery { gatewaySwitch.delete(any(), any()) } just runs

        val result = TestFilter().deleteAll(setOf(target), gatewaySwitch, setOf(target))

        // Not recursive = false: over SAF that refuses a populated directory, and nothing else
        // removes the directory itself.
        coVerify { gatewaySwitch.delete(target.path, recursive = true) }
        result.success shouldContainExactly listOf(target)
        result.failed.shouldBeEmpty()
    }

    @Test
    fun `matches below a deleted directory count as deleted too`() = runTest {
        val root = match("/storage/emulated/0/Android/data/com.test.pkg/cache/dir")
        val child = match("/storage/emulated/0/Android/data/com.test.pkg/cache/dir/file", FileType.FILE)
        coEvery { gatewaySwitch.delete(any(), any()) } just runs

        val result = TestFilter().deleteAll(setOf(root, child), gatewaySwitch, setOf(root, child))

        // Only the distinct root is deleted, the subtree goes with it.
        coVerify(exactly = 1) { gatewaySwitch.delete(any(), any()) }
        coVerify { gatewaySwitch.delete(root.path, recursive = true) }
        result.success shouldContainExactlyInAnyOrder listOf(root, child)
    }

    @Test
    fun `a failing root does not stop the other roots`() = runTest {
        val failing = match("/storage/emulated/0/Android/data/com.test.pkg/cache/broken")
        val other = match("/storage/emulated/0/Android/data/com.test.pkg/cache/fine")
        val failure = WriteException(path = failing.path)
        coEvery { gatewaySwitch.delete(failing.path, any()) } throws failure
        coEvery { gatewaySwitch.delete(other.path, any()) } just runs
        // The post-failure check: the directory is still there, so this really failed.
        coEvery { gatewaySwitch.exists(failing.path) } returns true

        val result = TestFilter().deleteAll(setOf(failing, other), gatewaySwitch, setOf(failing, other))

        coVerify { gatewaySwitch.delete(other.path, recursive = true) }
        result.success shouldContainExactly listOf(other)
        result.failed.single().apply {
            first shouldBe failing
            second.shouldBeInstanceOf<WriteException>()
        }
    }

    @Test
    fun `a failed delete counts as success when the target is gone anyway`() = runTest {
        val target = match("/storage/emulated/0/Android/data/com.test.pkg/cache/dir")
        coEvery { gatewaySwitch.delete(any(), any()) } throws WriteException(path = target.path)
        coEvery { gatewaySwitch.exists(target.path) } returns false

        val result = TestFilter().deleteAll(setOf(target), gatewaySwitch, setOf(target))

        result.success shouldContainExactly listOf(target)
        result.failed.shouldBeEmpty()
    }
}
