package eu.darken.sdmse.exclusion.core

import eu.darken.sdmse.common.files.saf.SAFPath
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import eu.darken.sdmse.exclusion.core.types.excludeNested
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication

/**
 * SAF half of [ExclusionExtensionsTest]. Separate class because `SAFPath` parses its tree URI with
 * `android.net.Uri`, which needs Robolectric (and therefore JUnit 4).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class ExclusionExtensionsSafTest : BaseTest() {

    private val treeA = "content://com.android.externalstorage.documents/tree/primary%3A"
    private val treeB = "content://com.android.externalstorage.documents/tree/1234-5678%3A"

    @Test
    fun `exclude nested - path - saf path`() = runTest {
        val excls = listOf<Exclusion.Path>(
            PathExclusion(SAFPath.build(treeA, "root", "test", "item", "sub1")),
        )
        val paths = setOf(
            SAFPath.build(treeA, "altroot"),
            SAFPath.build(treeA, "root"),
            SAFPath.build(treeA, "root", "test"),
            SAFPath.build(treeA, "root", "test", "item"),
            SAFPath.build(treeA, "root", "test", "item", "sub1"),
            SAFPath.build(treeA, "root", "test", "item", "sub2"),
            // Same segments, other tree: neither excluded nor pruned as an ancestor.
            SAFPath.build(treeB, "root", "test", "item"),
        )

        excls.excludeNested(paths) shouldBe setOf(
            SAFPath.build(treeA, "altroot"),
            SAFPath.build(treeA, "root", "test", "item", "sub2"),
            SAFPath.build(treeB, "root", "test", "item"),
        )
    }

    @Test
    fun `exclude nested - path - saf segment may contain any character`() = runTest {
        // The exclusion is a single segment that a delimiter-based key encoding would confuse with
        // the two segments below, which would drop the file and then prune its parent too.
        val excls = listOf<Exclusion.Path>(
            PathExclusion(SAFPath.build(treeA, "a\u0000b")),
        )
        val paths = setOf(
            SAFPath.build(treeA, "a"),
            SAFPath.build(treeA, "a", "b", "file"),
        )

        excls.excludeNested(paths) shouldBe setOf(
            SAFPath.build(treeA, "a"),
            SAFPath.build(treeA, "a", "b", "file"),
        )
    }
}
