package eu.darken.sdmse.common.files

import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.saf.SAFPath
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class APathAncestorIndexTest : BaseTest() {

    // Same URI path components, different authorities: their paths match, but they are different trees.
    private val treeA = "content://com.android.externalstorage.documents/tree/primary%3A"
    private val treeB = "content://com.example.otherprovider/tree/primary%3A"
    private val treeC = "content://com.android.externalstorage.documents/tree/1234-5678%3A"

    private val fixtures: List<APath> by lazy {
        listOf(
            LocalPath(File("/")),
            LocalPath(File("/a")),
            LocalPath(File("/a/b")),
            LocalPath(File("/a/bc")),
            LocalPath(File("/a/b c")),
            LocalPath(File("/a/b/c")),
            LocalPath(File("a/b")),
            LocalPath(File("a")),
            LocalPath(File("")),
            // Same path string as a SAF fixture below, different type.
            LocalPath(File("/tree/primary:/a")),
            SAFPath.build(treeA),
            SAFPath.build(treeA, "a"),
            SAFPath.build(treeA, "a", "b"),
            SAFPath.build(treeA, "a", "b", "c"),
            SAFPath.build(treeA, "a/b"),
            SAFPath.build(treeA, ""),
            SAFPath.build(treeA, "", "a"),
            SAFPath.build(treeB),
            SAFPath.build(treeB, "a"),
            SAFPath.build(treeB, "a", "b"),
            SAFPath.build(treeC, "a"),
            RawPath(""),
            RawPath("/"),
            RawPath("/a"),
            RawPath("/a/"),
            RawPath("/a/b"),
        )
    }

    private fun clue(a: APath, x: APath) = "A=${a.pathType}'${a.path}' X=${x.pathType}'${x.path}'"

    @Test
    fun `fixtures cover cross-tree SAF matches`() {
        SAFPath.build(treeA, "a").matches(SAFPath.build(treeB, "a")) shouldBe true
        SAFPath.build(treeA).isAncestorOf(SAFPath.build(treeB, "a")) shouldBe false
        SAFPath.build(treeA, "a/b").matches(SAFPath.build(treeA, "a", "b")) shouldBe true
    }

    @Test
    fun `ancestorsOf agrees with isAncestorOf for every pair`() {
        val index = APathAncestorIndex<APath> { it }.apply { fixtures.forEach { add(it) } }

        fixtures.forEach { x ->
            val expected = fixtures.filter { it.isAncestorOf(x) }
            val actual = index.ancestorsOf(x)
            withClue("X=${x.pathType}'${x.path}'") {
                actual shouldContainExactlyInAnyOrder expected
                index.hasAncestorOf(x) shouldBe expected.isNotEmpty()
            }
        }
    }

    @Test
    fun `single item index agrees with isAncestorOf and matches for every pair`() {
        fixtures.forEach { a ->
            val index = APathAncestorIndex<APath> { it }.apply { add(a) }
            fixtures.forEach { x ->
                withClue(clue(a, x)) {
                    (a in index.ancestorsOf(x)) shouldBe a.isAncestorOf(x)
                    index.hasAncestorOf(x) shouldBe a.isAncestorOf(x)
                    index.hasAncestorOrMatchOf(x) shouldBe (a.isAncestorOf(x) || a.matches(x))
                }
            }
        }
    }

    @Test
    fun `items sharing a path are all returned`() {
        data class Item(val id: Int, val path: APath)

        val first = Item(1, LocalPath.build("a"))
        val second = Item(2, LocalPath.build("a"))
        val other = Item(3, LocalPath.build("b"))
        val index = APathAncestorIndex<Item> { it.path }.apply {
            add(first)
            add(second)
            add(other)
        }

        index.ancestorsOf(LocalPath.build("a", "file")) shouldContainExactlyInAnyOrder listOf(first, second)
        index.ancestorsOf(LocalPath.build("a")) shouldBe emptyList()
        index.hasAncestorOrMatchOf(LocalPath.build("a")) shouldBe true
        index.hasAncestorOrMatchOf(LocalPath.build("c")) shouldBe false
    }
}
