package eu.darken.sdmse.common.files.core.saf

import android.net.Uri
import eu.darken.sdmse.common.files.core.FileType
import eu.darken.sdmse.common.files.core.matches
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class SAFPathExtensionsTest : BaseTest() {

    private val testUri1: Uri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3Asafstor")
    private val testUri2: Uri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3Asafstor")

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
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
            target = null,
        )
        val lookup2 = SAFPathLookup(
            lookedUp = SAFPath.build(testUri1, "seg1", "alt"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
            target = null,
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
}