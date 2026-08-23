package eu.darken.sdmse.exclusion.core.types

import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.saf.SAFPath
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication

/**
 * SAF half of [PathExclusionIndexTest]. Separate class because `SAFPath` parses its tree URI with
 * `android.net.Uri`, which needs Robolectric (and therefore JUnit 4).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class PathExclusionIndexSafTest : BaseTest() {

    private val treeA = "content://com.android.externalstorage.documents/tree/primary%3A"
    private val treeB = "content://com.android.externalstorage.documents/tree/1234-5678%3A"

    private suspend fun assertMatch(exclusion: PathExclusion, candidate: APath, expected: Boolean) {
        val reference = listOf<Exclusion.Path>(exclusion).any { it.match(candidate) }
        reference shouldBe expected
        PathExclusionIndex(listOf(exclusion)).matches(candidate) shouldBe expected
    }

    @Test
    fun `saf - exact match`() = runTest {
        assertMatch(
            PathExclusion(SAFPath.build(treeA, "a", "b")),
            SAFPath.build(treeA, "a", "b"),
            true,
        )
    }

    @Test
    fun `saf - descendant`() = runTest {
        assertMatch(
            PathExclusion(SAFPath.build(treeA, "a", "b")),
            SAFPath.build(treeA, "a", "b", "c", "d"),
            true,
        )
    }

    @Test
    fun `saf - tree root exclusion covers everything below it`() = runTest {
        assertMatch(
            PathExclusion(SAFPath.build(treeA)),
            SAFPath.build(treeA, "a"),
            true,
        )
    }

    @Test
    fun `saf - parent of the excluded path`() = runTest {
        assertMatch(
            PathExclusion(SAFPath.build(treeA, "a", "b")),
            SAFPath.build(treeA, "a"),
            false,
        )
    }

    @Test
    fun `saf - shared segment prefix without a boundary`() = runTest {
        assertMatch(
            PathExclusion(SAFPath.build(treeA, "a", "b")),
            SAFPath.build(treeA, "a", "bc"),
            false,
        )
    }

    @Test
    fun `saf - a segment may contain any character`() = runTest {
        // SAF display names come verbatim from the DocumentsProvider cursor, so a single segment can
        // hold what looks like two segments to a delimiter-based key encoding.
        assertMatch(
            PathExclusion(SAFPath.build(treeA, "a\u0000b")),
            SAFPath.build(treeA, "a", "b", "file"),
            false,
        )
    }

    @Test
    fun `saf - same segments under a different tree`() = runTest {
        assertMatch(
            PathExclusion(SAFPath.build(treeA, "a", "b")),
            SAFPath.build(treeB, "a", "b"),
            false,
        )
        assertMatch(
            PathExclusion(SAFPath.build(treeA, "a", "b")),
            SAFPath.build(treeB, "a", "b", "c"),
            false,
        )
    }
}
