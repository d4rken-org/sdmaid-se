package eu.darken.sdmse.appcleaner.core.forensics

import eu.darken.sdmse.appcleaner.core.forensics.filter.DefaultCachesPublicFilter
import eu.darken.sdmse.common.JUnitLogger
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.debug.logging.Logging
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertTimeoutPreemptively
import testhelpers.BaseTest
import java.time.Duration
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

    private fun match(
        path: String,
        fileType: FileType = FileType.DIRECTORY,
        size: Long = 16L,
        identifier: ExpendablesFilterIdentifier = TestFilter::class,
    ): ExpendablesFilter.Match.Deletion = ExpendablesFilter.Match.Deletion(
        identifier = identifier,
        lookup = LocalPathLookup(
            lookedUp = LocalPath.build(path),
            fileType = fileType,
            size = size,
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

    @Test
    fun `untargeted matches below a deleted root count as deleted, a name-prefix sibling does not`() = runTest {
        val root = match("/storage/emulated/0/Android/data/com.test.pkg/cache/dir")
        val child = match("/storage/emulated/0/Android/data/com.test.pkg/cache/dir/file", FileType.FILE)
        val nested = match("/storage/emulated/0/Android/data/com.test.pkg/cache/dir/sub/file", FileType.FILE)
        val sibling = match("/storage/emulated/0/Android/data/com.test.pkg/cache/dir2/file", FileType.FILE)
        coEvery { gatewaySwitch.delete(any(), any()) } just runs

        val result = TestFilter().deleteAll(
            targets = listOf(root),
            gatewaySwitch = gatewaySwitch,
            allMatches = listOf(sibling, nested, root, child),
        )

        coVerify(exactly = 1) { gatewaySwitch.delete(any(), any()) }
        result.success shouldContainExactlyInAnyOrder listOf(root, nested, child)
        result.failed.shouldBeEmpty()
    }

    @Test
    fun `a failed root keeps only the sub matches that are gone`() = runTest {
        val root = match("/storage/emulated/0/Android/data/com.test.pkg/cache/dir")
        val gone = match("/storage/emulated/0/Android/data/com.test.pkg/cache/dir/gone", FileType.FILE)
        val present = match("/storage/emulated/0/Android/data/com.test.pkg/cache/dir/sub/present", FileType.FILE)
        val sibling = match("/storage/emulated/0/Android/data/com.test.pkg/cache/dir2/file", FileType.FILE)
        coEvery { gatewaySwitch.delete(root.path, any()) } throws WriteException(path = root.path)
        coEvery { gatewaySwitch.exists(root.path) } returns true
        coEvery { gatewaySwitch.exists(gone.path) } returns false
        coEvery { gatewaySwitch.exists(present.path) } returns true

        val result = TestFilter().deleteAll(
            targets = listOf(root),
            gatewaySwitch = gatewaySwitch,
            allMatches = listOf(root, gone, present, sibling),
        )

        coVerify(exactly = 0) { gatewaySwitch.exists(sibling.path) }
        result.success shouldContainExactly listOf(gone)
        result.failed.single().apply {
            first shouldBe root
            second.shouldBeInstanceOf<WriteException>()
        }
    }

    @Test
    fun `duplicate targets resolve to the first target carrying the last lookup of a path`() = runTest {
        val okPath = "/storage/emulated/0/Android/data/com.test.pkg/cache/ok"
        val okStale = match(okPath, size = 10L)
        val okFirst = match(okPath, size = 20L)
        val okSecond = match(okPath, size = 20L, identifier = DefaultCachesPublicFilter::class)

        val brokenPath = "/storage/emulated/0/Android/data/com.test.pkg/cache/broken"
        val brokenStale = match(brokenPath, size = 30L)
        val brokenFirst = match(brokenPath, size = 40L)
        val brokenSecond = match(brokenPath, size = 40L, identifier = DefaultCachesPublicFilter::class)

        coEvery { gatewaySwitch.delete(okFirst.path, any()) } just runs
        coEvery { gatewaySwitch.delete(brokenFirst.path, any()) } throws WriteException(path = brokenFirst.path)
        coEvery { gatewaySwitch.exists(brokenFirst.path) } returns true

        val targets = listOf(okStale, okFirst, okSecond, brokenStale, brokenFirst, brokenSecond)
        val result = TestFilter().deleteAll(targets, gatewaySwitch, targets)

        coVerify(exactly = 2) { gatewaySwitch.delete(any(), any()) }
        result.success shouldContainExactly listOf(okFirst)
        result.success.single().apply {
            identifier shouldBe TestFilter::class
            expectedGain shouldBe 20L
        }
        result.failed.size shouldBe 1
        result.failed.single().first.apply {
            this shouldBe brokenFirst
            identifier shouldBe TestFilter::class
            expectedGain shouldBe 40L
        }
    }

    @Test
    fun `flat sibling matches are grouped without a pairwise scan`() {
        val targets = (0 until FLAT_COUNT).map {
            match("/storage/emulated/0/Android/data/com.test.pkg/files/photos/file$it", FileType.FILE)
        }
        val gateway = mockk<GatewaySwitch>(relaxed = true)
        val filter = TestFilter()

        val result = withQuietLogging {
            assertTimeoutPreemptively(BOUND) {
                runBlocking { filter.deleteAll(targets, gateway, targets) }
            }
        }

        result.success.size shouldBe FLAT_COUNT
        result.failed.shouldBeEmpty()
    }

    private inline fun <T> withQuietLogging(block: () -> T): T {
        Logging.clearAll()
        Logging.install(JUnitLogger(minLogLevel = Logging.Priority.WARN))
        try {
            return block()
        } finally {
            Logging.clearAll()
            Logging.install(JUnitLogger())
        }
    }

    companion object {
        // A pairwise scan over 10^5 matches (~5 * 10^9 comparisons) cannot meet this bound.
        private val BOUND: Duration = Duration.ofSeconds(10)
        private const val FLAT_COUNT = 100_000
    }
}
