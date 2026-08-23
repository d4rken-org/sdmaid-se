package eu.darken.sdmse.exclusion.core.types

import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.RawPath
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.segs
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.io.File

/**
 * Each path case runs against an index that holds only the one exclusion under test, so a broken
 * prefix rule can't hide behind an unrelated hit. Every case is also asserted against the linear
 * `any { it.match(...) }` it replaces.
 */
class PathExclusionIndexTest : BaseTest() {

    private suspend fun assertMatch(exclusion: PathExclusion, candidate: APath, expected: Boolean) {
        val reference = listOf<Exclusion.Path>(exclusion).any { it.match(candidate) }
        reference shouldBe expected
        PathExclusionIndex(listOf(exclusion)).matches(candidate) shouldBe expected
    }

    private fun local(path: String) = LocalPath(File(path))

    @Test
    fun `local - exact match`() = runTest {
        assertMatch(PathExclusion(local("/a/b")), local("/a/b"), true)
    }

    @Test
    fun `local - direct child`() = runTest {
        assertMatch(PathExclusion(local("/a/b")), local("/a/b/c"), true)
    }

    @Test
    fun `local - deep descendant`() = runTest {
        assertMatch(PathExclusion(local("/a/b")), local("/a/b/c/d/e"), true)
    }

    @Test
    fun `local - shared string prefix without a separator boundary`() = runTest {
        assertMatch(PathExclusion(local("/a/b")), local("/a/bc/d"), false)
    }

    @Test
    fun `local - parent of the excluded path`() = runTest {
        assertMatch(PathExclusion(local("/a/b")), local("/a"), false)
    }

    @Test
    fun `local - root exclusion covers everything below it`() = runTest {
        assertMatch(PathExclusion(local("/")), local("/a"), true)
        assertMatch(PathExclusion(local("/")), local("/a/b"), true)
        assertMatch(PathExclusion(local("/")), local("/"), true)
    }

    @Test
    fun `local - empty path is an ancestor of any absolute path`() = runTest {
        assertMatch(PathExclusion(local("")), local("/a"), true)
    }

    @Test
    fun `raw - exact match`() = runTest {
        assertMatch(PathExclusion(RawPath("/a/b")), RawPath("/a/b"), true)
    }

    @Test
    fun `raw - deep descendant`() = runTest {
        assertMatch(PathExclusion(RawPath("/a/b")), RawPath("/a/b/c/d"), true)
    }

    @Test
    fun `raw - shared string prefix without a separator boundary`() = runTest {
        assertMatch(PathExclusion(RawPath("/a/b")), RawPath("/a/bc/d"), false)
    }

    @Test
    fun `raw - root is not an ancestor`() = runTest {
        // The generic RAW branch is `descendant.path.startsWith(this.path + "/")`, so "/" would need
        // the candidate to start with "//". There is no root special case like LOCAL has.
        assertMatch(PathExclusion(RawPath("/")), RawPath("/a"), false)
    }

    @Test
    fun `raw - empty path is an ancestor of any absolute path`() = runTest {
        assertMatch(PathExclusion(RawPath("")), RawPath("/a"), true)
    }

    @Test
    fun `raw - relative exclusion does not match an absolute candidate`() = runTest {
        // RawPathExtensions.isAncestorOf would absolutize both sides and match, but PathExclusion
        // goes through the generic APath branch, which compares the strings as they are.
        assertMatch(PathExclusion(RawPath("a")), RawPath("/cwd/a/b"), false)
    }

    @Test
    fun `raw - repeated separators in the candidate`() = runTest {
        assertMatch(PathExclusion(RawPath("/a")), RawPath("/a//b"), true)
    }

    @Test
    fun `raw - trailing separator on the exclusion`() = runTest {
        assertMatch(PathExclusion(RawPath("/a/")), RawPath("/a/b"), false)
    }

    @Test
    fun `cross type - a local exclusion never matches a raw candidate`() = runTest {
        assertMatch(PathExclusion(local("/a/b")), RawPath("/a/b"), false)
        assertMatch(PathExclusion(RawPath("/a/b")), local("/a/b"), false)
    }

    @Test
    fun `non indexable exclusions still match through the fallback`() = runTest {
        val index = PathExclusionIndex(
            listOf(SegmentExclusion(segs("cache"), allowPartial = false, ignoreCase = true)),
        )

        index.matches(local("/a/cache/b")) shouldBe true
        index.matches(local("/a/b")) shouldBe false
    }

    @Test
    fun `the result does not depend on the exclusion order`() = runTest {
        val segment = SegmentExclusion(segs("cache"), allowPartial = false, ignoreCase = true)
        val path = PathExclusion(local("/a/b"))

        PathExclusionIndex(listOf(segment, path)).matches(local("/a/b/c")) shouldBe true
        PathExclusionIndex(listOf(path, segment)).matches(local("/a/b/c")) shouldBe true
        PathExclusionIndex(listOf(segment, path)).matches(local("/x/cache")) shouldBe true
        PathExclusionIndex(listOf(path, segment)).matches(local("/x/cache")) shouldBe true
        PathExclusionIndex(listOf(segment, path)).matches(local("/x/y")) shouldBe false
        PathExclusionIndex(listOf(path, segment)).matches(local("/x/y")) shouldBe false
    }

    @Test
    fun `a raw candidate is answered by the path exclusion instead of throwing`() = runTest {
        // RawPath.segments throws NotImplementedError, so the old order-dependent `any { }` threw
        // whenever a SegmentExclusion was evaluated before the matching PathExclusion.
        val exclusions = listOf<Exclusion.Path>(
            SegmentExclusion(segs("cache"), allowPartial = false, ignoreCase = true),
            PathExclusion(RawPath("/a/b")),
        )

        PathExclusionIndex(exclusions).matches(RawPath("/a/b/c")) shouldBe true
        PathExclusionIndex(exclusions.reversed()).matches(RawPath("/a/b/c")) shouldBe true
    }
}
