package eu.darken.sdmse.exclusion.core

import eu.darken.sdmse.common.files.RawPath
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.segs
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import eu.darken.sdmse.exclusion.core.types.SegmentExclusion
import eu.darken.sdmse.exclusion.core.types.excludeNested
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.io.File

class ExclusionExtensionsTest : BaseTest() {
    private val testFile = File(IO_TEST_BASEDIR, "testfile")

    @AfterEach
    fun cleanup() {
        testFile.delete()
    }

    @Test
    fun `exclude nested - segment - local path`() = runTest {
        val excl = SegmentExclusion(segs("path", "item"), allowPartial = false, ignoreCase = true)
        val paths = setOf(
            LocalPath.build("altroot"),
            LocalPath.build("root"),
            LocalPath.build("root", "test"),
            LocalPath.build("root", "test", "path"),
            LocalPath.build("root", "test", "path", "item", "subitem"),
            LocalPath.build("root", "alt"),
            LocalPath.build("root", "alt", "path"),
            LocalPath.build("root", "alt", "path", "item"),
            LocalPath.build("root", "alt", "path", "item", "subitem"),
        )

        excl.excludeNested(paths) shouldBe setOf(
            LocalPath.build("altroot"),
        )
    }

    @Test
    fun `exclude nested - path - local path`() = runTest {
        val excl = PathExclusion(LocalPath.build("root", "test", "path"))
        val paths = setOf(
            LocalPath.build("root"),
            LocalPath.build("root", "test"),
            LocalPath.build("root", "test", "path"),
            LocalPath.build("root", "test", "path", "item"),
            LocalPath.build("root", "test", "path", "item", "subitem"),
            LocalPath.build("root", "alt"),
            LocalPath.build("root", "alt", "path"),
            LocalPath.build("root", "alt", "path", "item"),
            LocalPath.build("root", "alt", "path", "item", "subitem"),
        )

        excl.excludeNested(paths) shouldBe setOf(
            LocalPath.build("root", "alt"),
            LocalPath.build("root", "alt", "path"),
            LocalPath.build("root", "alt", "path", "item"),
            LocalPath.build("root", "alt", "path", "item", "subitem"),
        )
    }

    @Test
    fun `exclude multiple nested - segment - local path`() = runTest {
        val excls = setOf(
            SegmentExclusion(segs("subitem1"), allowPartial = false, ignoreCase = true),
            SegmentExclusion(segs("subitem2"), allowPartial = false, ignoreCase = true),
        )
        val paths = setOf(
            LocalPath.build("altroot"),
            LocalPath.build("root", "test"),
            LocalPath.build("root", "test", "path"),
            LocalPath.build("root", "test", "path", "item"),
            LocalPath.build("root", "test", "path", "item", "subitem1"),
            LocalPath.build("root", "test", "path", "item", "subitem2"),
            LocalPath.build("root", "test", "path", "item", "subitem3"),
            LocalPath.build("root", "alt"),
            LocalPath.build("root", "alt", "path"),
            LocalPath.build("root", "alt", "path", "item"),
            LocalPath.build("root", "alt", "path", "item", "subitem1"),
            LocalPath.build("root", "alt", "path", "item", "subitem2"),
            LocalPath.build("root", "alt", "path", "item", "subitem3"),
        )

        excls.excludeNested(paths) shouldBe setOf(
            LocalPath.build("altroot"),
            LocalPath.build("root", "test", "path", "item", "subitem3"),
            LocalPath.build("root", "alt", "path", "item", "subitem3"),
        )
    }

    @Test
    fun `exclude multiple nested - path - local path`() = runTest {
        val excls = setOf(
            PathExclusion(LocalPath.build("root", "test", "path", "item", "subitem1")),
            PathExclusion(LocalPath.build("root", "alt", "path", "item", "subitem1")),
        )
        val paths = setOf(
            LocalPath.build("altroot"),
            LocalPath.build("root"),
            LocalPath.build("root", "test"),
            LocalPath.build("root", "test", "path"),
            LocalPath.build("root", "test", "path", "item"),
            LocalPath.build("root", "test", "path", "item", "subitem1"),
            LocalPath.build("root", "test", "path", "item", "subitem2"),
            LocalPath.build("root", "alt"),
            LocalPath.build("root", "alt", "path"),
            LocalPath.build("root", "alt", "path", "item"),
            LocalPath.build("root", "alt", "path", "item", "subitem1"),
            LocalPath.build("root", "alt", "path", "item", "subitem2"),
        )

        excls.excludeNested(paths) shouldBe setOf(
            LocalPath.build("altroot"),
            LocalPath.build("root", "test", "path", "item", "subitem2"),
            LocalPath.build("root", "alt", "path", "item", "subitem2"),
        )
    }

    @Test
    fun `exclude nested - no exclusions returns the input unchanged`() = runTest {
        val paths = setOf(
            LocalPath.build("root"),
            LocalPath.build("root", "test"),
        )

        emptyList<Exclusion.Path>().excludeNested(paths) shouldBe paths
    }

    @Test
    fun `exclude nested - ancestors of every excluded path are pruned`() = runTest {
        val excls = listOf(
            PathExclusion(LocalPath.build("root", "test", "path", "item")),
            PathExclusion(LocalPath.build("other", "deep", "item")),
        )
        val paths = setOf(
            LocalPath.build("altroot"),
            LocalPath.build("root"),
            LocalPath.build("root", "test"),
            LocalPath.build("root", "test", "path"),
            LocalPath.build("root", "test", "path", "item"),
            LocalPath.build("root", "test", "sibling"),
            LocalPath.build("other"),
            LocalPath.build("other", "deep"),
            LocalPath.build("other", "deep", "item"),
        )

        excls.excludeNested(paths) shouldBe setOf(
            LocalPath.build("altroot"),
            LocalPath.build("root", "test", "sibling"),
        )
    }

    @Test
    fun `exclude nested - mixed segment and path exclusions`() = runTest {
        mixedExclusions().excludeNested(mixedPaths()) shouldBe mixedExpectation()
    }

    @Test
    fun `exclude nested - the result does not depend on the exclusion order`() = runTest {
        permutations(mixedExclusions()).forEach { ordered ->
            ordered.excludeNested(mixedPaths()) shouldBe mixedExpectation()
        }
    }

    @Test
    fun `exclude nested - path - raw path`() = runTest {
        val excls = listOf(PathExclusion(RawPath("/root/test/item/sub1")))
        val paths = setOf(
            RawPath("/altroot"),
            RawPath("/root"),
            RawPath("/root/test"),
            RawPath("/root/test/item"),
            RawPath("/root/test/item/sub1"),
            RawPath("/root/test/item/sub2"),
        )

        excls.excludeNested(paths) shouldBe setOf(
            RawPath("/altroot"),
            RawPath("/root/test/item/sub2"),
        )
    }

    // Same set in every order. The expectation is the one the previous per-exclusion fold produced.
    private fun mixedExclusions(): List<Exclusion.Path> = listOf(
        PathExclusion(LocalPath.build("root", "test", "path", "item", "sub1")),
        SegmentExclusion(segs("cache"), allowPartial = false, ignoreCase = true),
        PathExclusion(LocalPath.build("root", "test", "path", "item", "sub2")),
    )

    private fun mixedPaths(): Set<LocalPath> = setOf(
        LocalPath.build("altroot"),
        LocalPath.build("root"),
        LocalPath.build("root", "test"),
        LocalPath.build("root", "test", "path"),
        LocalPath.build("root", "test", "path", "item"),
        LocalPath.build("root", "test", "path", "item", "sub1"),
        LocalPath.build("root", "test", "path", "item", "sub2"),
        LocalPath.build("root", "test", "path", "item", "sub3"),
        LocalPath.build("root", "alt"),
        LocalPath.build("root", "alt", "cache"),
        LocalPath.build("root", "alt", "cache", "file"),
    )

    private fun mixedExpectation(): Set<LocalPath> = setOf(
        LocalPath.build("altroot"),
        LocalPath.build("root", "test", "path", "item", "sub3"),
    )

    private fun <T> permutations(items: List<T>): List<List<T>> {
        if (items.size <= 1) return listOf(items)
        return items.indices.flatMap { index ->
            val rest = items.toMutableList().apply { removeAt(index) }
            permutations(rest).map { listOf(items[index]) + it }
        }
    }
}
