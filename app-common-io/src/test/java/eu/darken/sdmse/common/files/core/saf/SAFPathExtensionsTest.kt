package eu.darken.sdmse.common.files.core.saf

import android.net.Uri
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class SAFPathExtensionsTest {

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
        parent.isParentOf(SAFPath.build(testUri1, "the")) shouldBe false
        parent.isParentOf(SAFPath.build(testUri1, "the", "parent")) shouldBe false
        parent.isParentOf(SAFPath.build(testUri1, "the", "parent2")) shouldBe false
        parent.isParentOf(SAFPath.build(testUri1, "the", "parent", "child")) shouldBe true
        parent.isParentOf(SAFPath.build(testUri1, "the", "parent", "child", "child")) shouldBe true
        parent.isParentOf(SAFPath.build(testUri1, "the", "parent", "child1", "child2")) shouldBe true
    }
}