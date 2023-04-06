package eu.darken.sdmse.common.files

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SimplePathExtensionsTest {

    @Test
    fun `test chunking`() {
        val parent = RawPath.build("/the/parent/")
        val child = RawPath.build("/the/parent/has/a/child/")

        val crumbs = parent.crumbsTo(child)

        crumbs shouldBe arrayOf("has", "a", "child")
    }

}