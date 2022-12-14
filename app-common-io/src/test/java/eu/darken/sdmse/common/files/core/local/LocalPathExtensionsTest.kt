package eu.darken.sdmse.common.files.core.local

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class LocalPathExtensionsTest {


    @Test
    fun `test chunking`() {
        val parent = LocalPath.build("/the/parent")
        val child = LocalPath.build("/the/parent/has/a/child")

        val crumbs = parent.crumbsTo(child)

        crumbs shouldBe arrayOf("has", "a", "child")
    }

    @Test
    fun `parent child relationship`() {
        val parent = LocalPath.build("base", "the", "parent")
        parent.isParentOf(LocalPath.build("base", "the")) shouldBe false
        parent.isParentOf(LocalPath.build("base", "the", "parent")) shouldBe false
        parent.isParentOf(LocalPath.build("base", "the", "parent2")) shouldBe false
        parent.isParentOf(LocalPath.build("base", "the", "parent", "child")) shouldBe true
        parent.isParentOf(LocalPath.build("base", "the", "parent", "child", "child")) shouldBe true
        parent.isParentOf(LocalPath.build("base", "the", "parent", "child1", "child2")) shouldBe true
    }

}