package eu.darken.sdmse.common.files

import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertTimeoutPreemptively
import testhelpers.BaseTest
import java.time.Duration
import java.time.Instant

/**
 * The bound has wide headroom over a single pass, but a pairwise ancestor check over 10^5 paths
 * (~5 * 10^9 comparisons) cannot meet it.
 */
class FilterDistinctRootsScaleTest : BaseTest() {

    private val appBase = LocalPath.build("storage", "emulated", "0", "Android", "data", "test.pkg")

    @Test
    fun `flat siblings`() {
        val photos = LocalPath.build(appBase, "files", "photos")
        val paths: List<APath> = (0 until FLAT_COUNT).map { LocalPath.build(photos, "file$it") }

        val roots = assertTimeoutPreemptively(BOUND) { paths.filterDistinctRoots() }

        roots.toList() shouldBe paths
    }

    @Test
    fun `flat sibling lookups`() {
        val photos = LocalPath.build(appBase, "files", "photos")
        val lookups: List<APathLookup<*>> = (0 until FLAT_COUNT).map {
            LocalPathLookup(
                lookedUp = LocalPath.build(photos, "file$it"),
                fileType = FileType.FILE,
                size = 0,
                modifiedAt = Instant.EPOCH,
                target = null,
            )
        }

        val roots = assertTimeoutPreemptively(BOUND) { lookups.filterDistinctRoots() }

        roots.toList() shouldBe lookups
    }

    @Test
    fun `nested matches below matched directories`() {
        val cache = LocalPath.build(appBase, "cache")
        val dirs = (0 until DIR_COUNT).map { LocalPath.build(cache, "dir$it") }
        val nested = dirs.flatMap { dir ->
            val sub = LocalPath.build(dir, "sub")
            listOf(sub) + (0 until FILES_PER_DIR).flatMap {
                listOf(LocalPath.build(dir, "file$it"), LocalPath.build(sub, "file$it"))
            }
        }
        // The parent of these files is not part of the input.
        val photos = LocalPath.build(appBase, "files", "photos")
        val loose = (0 until LOOSE_COUNT).map { LocalPath.build(photos, "loose$it") }
        val input: List<APath> = nested.reversed() + loose + dirs

        val roots = assertTimeoutPreemptively(BOUND) { input.filterDistinctRoots() }

        roots.size shouldBe DIR_COUNT + LOOSE_COUNT
        // Directories have one segment fewer than the loose files, so they sort first.
        roots.toList() shouldBe dirs + loose
        listOf(nested.first(), nested.last(), nested[nested.size / 2]).forEach { (it in roots) shouldBe false }
    }

    companion object {
        private val BOUND: Duration = Duration.ofSeconds(10)
        private const val FLAT_COUNT = 100_000
        private const val DIR_COUNT = 2_000
        private const val FILES_PER_DIR = 25
        private const val LOOSE_COUNT = 20_000
    }
}
