package eu.darken.sdmse.common.files.saf

import android.net.Uri
import eu.darken.sdmse.common.files.*
import eu.darken.sdmse.common.files.isAncestorOf
import eu.darken.sdmse.common.files.removePrefix
import eu.darken.sdmse.common.files.saf.*
import eu.darken.sdmse.common.files.startsWith
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class SAFPathExtensionsTest : BaseTest() {
    private val baseTreeUri = "content://com.android.externalstorage.documents/tree/primary%3A"

    private val testUri1 = "content://com.android.externalstorage.documents/tree/primary%3Asafstor"
    private val testUri2 = "content://com.android.externalstorage.documents/tree/primary%3Asafstor"

    @Test
    fun `test crumbsTo`() {
        val parent = SAFPath.build(testUri1, "the", "parent")
        val child = SAFPath.build(testUri1, "the", "parent", "has", "a", "child")

        val crumbs = parent.crumbsTo(child)

        crumbs shouldBe arrayOf("has", "a", "child")
    }

    @Test
    fun `test crumbsTo with empty parent`() {
        val parent = SAFPath.build(testUri1)
        val child = SAFPath.build(testUri1, "has", "a", "child")

        val crumbs = parent.crumbsTo(child)

        crumbs shouldBe arrayOf("has", "a", "child")
    }

    @Test
    fun `test crumbsTo with equal arguments`() {
        val parent = SAFPath.build(testUri1, "the", "parent")
        val child = SAFPath.build(testUri1, "the", "parent")

        val crumbs = parent.crumbsTo(child)

        crumbs shouldBe arrayOf()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test crumbsTo needs same root`() {
        val parent = SAFPath.build(testUri1, "/the/parent/")
        val child = SAFPath.build(testUri2, "/the/parent/has/a/child/")

        parent.crumbsTo(child)
    }

    @Test
    fun `test storage root detection`() {
        val nonRoot = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3Asafstor")
        SAFPath.build(nonRoot).isStorageRoot shouldBe false
        SAFPath.build(nonRoot, "crumb1").isStorageRoot shouldBe false

        val root = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3A")
        SAFPath.build(root).isStorageRoot shouldBe true
        SAFPath.build(root, "crumb1").isStorageRoot shouldBe false
    }

    @Test
    fun `is file a parent of another file`() {
        val parent = SAFPath.build(testUri1, "the", "parent")
        parent.isAncestorOf(SAFPath.build(testUri1, "the")) shouldBe false
        parent.isAncestorOf(SAFPath.build(testUri1, "the", "parent")) shouldBe false
        parent.isAncestorOf(SAFPath.build(testUri1, "the", "parent2")) shouldBe false
        parent.isAncestorOf(SAFPath.build(testUri1, "the", "parent", "child")) shouldBe true
        parent.isAncestorOf(SAFPath.build(testUri1, "the", "parent", "child", "child")) shouldBe true
        parent.isAncestorOf(SAFPath.build(testUri1, "the", "parent", "child1", "child2")) shouldBe true
    }

    @Test
    fun `match operator`() {
        val file1 = SAFPath.build(testUri1, "seg1", "seg2")
        val file2 = SAFPath.build(testUri1, "seg1", "alt")

        val lookup1 = SAFPathLookup(
            lookedUp = SAFPath.build(testUri1, "seg1", "seg2"),
            docFile = mockk(),
        )
        val lookup2 = SAFPathLookup(
            lookedUp = SAFPath.build(testUri1, "seg1", "alt"),
            docFile = mockk(),
        )
        file1.matches(file1) shouldBe true
        file1.matches(file2) shouldBe false
        file1.matches(lookup1) shouldBe true
        file1.matches(lookup2) shouldBe false
        lookup1.matches(file1) shouldBe true
        lookup1.matches(file2) shouldBe false
        lookup1.matches(lookup1) shouldBe true
        lookup1.matches(lookup2) shouldBe false
        file2.matches(lookup2) shouldBe true
    }


    @Test
    fun `isAncestorOf operator`() {
        val file1 = SAFPath.build(baseTreeUri, "parent")
        val file2 = SAFPath.build(baseTreeUri, "parent", "child", "niece")

        val lookup1 = SAFPathLookup(
            lookedUp = SAFPath.build(baseTreeUri, "parent"),
            docFile = mockk(),
        )
        val lookup2 = SAFPathLookup(
            lookedUp = SAFPath.build(baseTreeUri, "parent", "child", "niece"),
            docFile = mockk(),
        )

        file1.isAncestorOf(file1) shouldBe false
        file1.isAncestorOf(file2) shouldBe true
        file1.isAncestorOf(lookup1) shouldBe false
        file1.isAncestorOf(lookup2) shouldBe true

        file2.isAncestorOf(file1) shouldBe false
        file2.isAncestorOf(file2) shouldBe false
        file2.isAncestorOf(lookup1) shouldBe false
        file2.isAncestorOf(lookup2) shouldBe false

        lookup1.isAncestorOf(file1) shouldBe false
        lookup1.isAncestorOf(file2) shouldBe true
        lookup1.isAncestorOf(lookup1) shouldBe false
        lookup1.isAncestorOf(lookup2) shouldBe true

        lookup2.isAncestorOf(file1) shouldBe false
        lookup2.isAncestorOf(file2) shouldBe false
        lookup2.isAncestorOf(lookup1) shouldBe false
        lookup2.isAncestorOf(lookup2) shouldBe false
    }

    @Test
    fun `isDescendantOf operator`() {
        val file1 = SAFPath.build(baseTreeUri, "parent")
        val file2 = SAFPath.build(baseTreeUri, "parent", "child", "niece")

        val lookup1 = SAFPathLookup(
            lookedUp = SAFPath.build(baseTreeUri, "parent"),
            docFile = mockk(),
        )
        val lookup2 = SAFPathLookup(
            lookedUp = SAFPath.build(baseTreeUri, "parent", "child", "niece"),
            docFile = mockk(),
        )

        file1.isDescendantOf(file1) shouldBe false
        file1.isDescendantOf(file2) shouldBe false
        file1.isDescendantOf(lookup1) shouldBe false
        file1.isDescendantOf(lookup2) shouldBe false

        file2.isDescendantOf(file1) shouldBe true
        file2.isDescendantOf(file2) shouldBe false
        file2.isDescendantOf(lookup1) shouldBe true
        file2.isDescendantOf(lookup2) shouldBe false

        lookup1.isDescendantOf(file1) shouldBe false
        lookup1.isDescendantOf(file2) shouldBe false
        lookup1.isDescendantOf(lookup1) shouldBe false
        lookup1.isDescendantOf(lookup2) shouldBe false

        lookup2.isDescendantOf(file1) shouldBe true
        lookup2.isDescendantOf(file2) shouldBe false
        lookup2.isDescendantOf(lookup1) shouldBe true
        lookup2.isDescendantOf(lookup2) shouldBe false
    }

    @Test
    fun `isParentOf operator`() {
        val file1 = SAFPath.build(baseTreeUri, "parent")
        val file2 = SAFPath.build(baseTreeUri, "parent", "child")

        val lookup1 = SAFPathLookup(
            lookedUp = SAFPath.build(baseTreeUri, "parent"),
            docFile = mockk(),
        )
        val lookup2 = SAFPathLookup(
            lookedUp = SAFPath.build(baseTreeUri, "parent", "child"),
            docFile = mockk(),
        )

        file1.isParentOf(file1) shouldBe false
        file1.isParentOf(file2) shouldBe true
        file1.isParentOf(lookup1) shouldBe false
        file1.isParentOf(lookup2) shouldBe true

        file2.isParentOf(file1) shouldBe false
        file2.isParentOf(file2) shouldBe false
        file2.isParentOf(lookup1) shouldBe false
        file2.isParentOf(lookup2) shouldBe false

        lookup1.isParentOf(file1) shouldBe false
        lookup1.isParentOf(file2) shouldBe true
        lookup1.isParentOf(lookup1) shouldBe false
        lookup1.isParentOf(lookup2) shouldBe true

        lookup2.isParentOf(file1) shouldBe false
        lookup2.isParentOf(file2) shouldBe false
        lookup2.isParentOf(lookup1) shouldBe false
        lookup2.isParentOf(lookup2) shouldBe false
    }

    @Test
    fun `isChildOf operator`() {
        val file1 = SAFPath.build(baseTreeUri, "parent")
        val file2 = SAFPath.build(baseTreeUri, "parent", "child")

        val lookup1 = SAFPathLookup(
            lookedUp = SAFPath.build(baseTreeUri, "parent"),
            docFile = mockk(),
        )
        val lookup2 = SAFPathLookup(
            lookedUp = SAFPath.build(baseTreeUri, "parent", "child"),
            docFile = mockk(),
        )

        file1.isChildOf(file1) shouldBe false
        file1.isChildOf(file2) shouldBe false
        file1.isChildOf(lookup1) shouldBe false
        file1.isChildOf(lookup2) shouldBe false

        file2.isChildOf(file1) shouldBe true
        file2.isChildOf(file2) shouldBe false
        file2.isChildOf(lookup1) shouldBe true
        file2.isChildOf(lookup2) shouldBe false

        lookup1.isChildOf(file1) shouldBe false
        lookup1.isChildOf(file2) shouldBe false
        lookup1.isChildOf(lookup1) shouldBe false
        lookup1.isChildOf(lookup2) shouldBe false

        lookup2.isChildOf(file1) shouldBe true
        lookup2.isChildOf(file2) shouldBe false
        lookup2.isChildOf(lookup1) shouldBe true
        lookup2.isChildOf(lookup2) shouldBe false
    }

    @Test
    fun `startsWith operator`() {
        val baseUri = Uri.parse("content://com.android.externalstorage.documents/tree/4BBD-D3E7")
        val file1 = SAFPath.build(baseUri, "Android", "data", "eu.darken.octi")
        val file2 = SAFPath.build(baseUri, "Android", "data", "eu.darken.octi", "files")

        file1.startsWith(file1) shouldBe true
        file1.startsWith(file2) shouldBe false


        file2.startsWith(file1) shouldBe true
        file2.startsWith(file2) shouldBe true
    }

    @Test
    fun `startsWith regression test1`() {
        val file1 = SAFPath.build(baseTreeUri, "chi")
        val file2 = SAFPath.build(baseTreeUri, "child")

        val lookup1 = SAFPathLookup(
            lookedUp = SAFPath.build(baseTreeUri, "chi"),
            docFile = mockk(),
        )
        val lookup2 = SAFPathLookup(
            lookedUp = SAFPath.build(baseTreeUri, "child"),
            docFile = mockk(),
        )

        file1.startsWith(file1) shouldBe true
        file1.startsWith(file2) shouldBe false
        file1.startsWith(lookup1) shouldBe true
        file1.startsWith(lookup2) shouldBe false

        file2.startsWith(file1) shouldBe true
        file2.startsWith(file2) shouldBe true
        file2.startsWith(lookup1) shouldBe true
        file2.startsWith(lookup2) shouldBe true

        lookup1.startsWith(file1) shouldBe true
        lookup1.startsWith(file2) shouldBe false
        lookup1.startsWith(lookup1) shouldBe true
        lookup1.startsWith(lookup2) shouldBe false

        lookup2.startsWith(file1) shouldBe true
        lookup2.startsWith(file2) shouldBe true
        lookup2.startsWith(lookup1) shouldBe true
        lookup2.startsWith(lookup2) shouldBe true
    }

    @Test fun `remove prefix`() {
        val prefix = SAFPath.build(baseTreeUri, "pre", "fix")
        val pre = SAFPath.build(baseTreeUri, "pre")
        val prefixLookup = SAFPathLookup(
            lookedUp = SAFPath.build(baseTreeUri, "pre", "fix"),
            docFile = mockk(),
        )
        val preLookup = SAFPathLookup(
            lookedUp = SAFPath.build(baseTreeUri, "pre"),
            docFile = mockk(),
        )

        prefix.removePrefix(prefix) shouldBe segs()

        shouldThrow<IllegalArgumentException> {
            pre.removePrefix(prefix)
        }
        prefix.removePrefix(pre) shouldBe segs("fix")
        prefix.removePrefix(preLookup) shouldBe segs("fix")

        prefixLookup.removePrefix(prefixLookup) shouldBe segs()

        shouldThrow<IllegalArgumentException> {
            preLookup.removePrefix(prefixLookup)
        }
        prefixLookup.removePrefix(preLookup) shouldBe segs("fix")
        prefixLookup.removePrefix(pre) shouldBe segs("fix")
    }

    @Test fun `remove prefix with overlap`() {
        val prefix = SAFPath.build(baseTreeUri, "prefix", "overlap", "folder")
        val pre = SAFPath.build(baseTreeUri, "prefix", "overlap")
        val prefixLookup = SAFPathLookup(
            lookedUp = SAFPath.build(baseTreeUri, "prefix", "overlap", "folder"),
            docFile = mockk(),
        )
        val preLookup = SAFPathLookup(
            lookedUp = SAFPath.build(baseTreeUri, "prefix", "overlap"),
            docFile = mockk(),
        )

        prefix.removePrefix(prefix, overlap = 0) shouldBe prefix.removePrefix(prefix)

        prefix.removePrefix(prefix, overlap = 1) shouldBe segs("folder")

        shouldThrow<IllegalArgumentException> {
            pre.removePrefix(prefix, overlap = 1)
        }
        prefix.removePrefix(pre, overlap = 1) shouldBe segs("overlap", "folder")
        prefix.removePrefix(preLookup, overlap = 1) shouldBe segs("overlap", "folder")

        prefixLookup.removePrefix(prefixLookup, overlap = 1) shouldBe segs("folder")

        shouldThrow<IllegalArgumentException> {
            preLookup.removePrefix(prefixLookup)
        }
        prefixLookup.removePrefix(preLookup, overlap = 1) shouldBe segs("overlap", "folder")
        prefixLookup.removePrefix(pre, overlap = 1) shouldBe segs("overlap", "folder")
    }
}