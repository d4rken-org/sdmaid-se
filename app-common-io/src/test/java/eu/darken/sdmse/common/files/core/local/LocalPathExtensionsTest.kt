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

}