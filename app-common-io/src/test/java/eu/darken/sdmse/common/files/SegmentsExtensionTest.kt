package eu.darken.sdmse.common.files

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class SegmentsExtensionTest : BaseTest() {

    @Test fun `segment matches`() {
        emptyList<String>().matches(emptyList()) shouldBe true
        null.matches(segs("abc", "def")) shouldBe false
        segs("abc", "def").matches(null) shouldBe false
        segs("abc", "def").matches(segs("abc", "def")) shouldBe true
        segs("abc", "DEF").matches(segs("abc", "def")) shouldBe false
        segs("abc", "DEF").matches(segs("abc", "def"), ignoreCase = true) shouldBe true
    }

    @Test fun `segment isAncestorOf`() {
        emptyList<String>().isAncestorOf(emptyList()) shouldBe false
        null.isAncestorOf(segs("abc", "def")) shouldBe false
        segs("abc", "def").isAncestorOf(null) shouldBe false
        segs("abc").isAncestorOf(segs("abc", "def")) shouldBe true
        segs("ABC").isAncestorOf(segs("abc", "def")) shouldBe false
        segs("ABC").isAncestorOf(segs("abc", "def"), ignoreCase = true) shouldBe true
    }

    @Test fun `segment isParentOf`() {
        emptyList<String>().isParentOf(emptyList()) shouldBe false
        null.isParentOf(segs("abc", "def", "ghi")) shouldBe false
        segs("abc", "def", "ghi").isParentOf(null) shouldBe false
        segs("abc", "def").isParentOf(segs("abc", "def", "ghi")) shouldBe true
        segs("abc").isParentOf(segs("abc", "def", "ghi")) shouldBe false
        segs("ABC").isParentOf(segs("abc", "def")) shouldBe false
        segs("ABC").isParentOf(segs("abc", "def"), ignoreCase = true) shouldBe true
    }

    @Test fun `segment isDescendentOf`() {
        emptyList<String>().isDescendentOf(emptyList()) shouldBe false
        null.isDescendentOf(segs("abc", "def")) shouldBe false
        segs("abc", "def").isDescendentOf(null) shouldBe false
        segs("abc", "def").isDescendentOf(segs("abc")) shouldBe true
        segs("abc", "def").isDescendentOf(segs("def")) shouldBe false
        segs("ABC", "def").isDescendentOf(segs("abc")) shouldBe false
        segs("ABC", "def").isDescendentOf(segs("abc"), ignoreCase = true) shouldBe true
        segs("def").isDescendentOf(segs("abc"), ignoreCase = true) shouldBe false
        segs("def").isDescendentOf(segs("abc", "def"), ignoreCase = true) shouldBe false
        segs("def").isDescendentOf(segs("abc"), ignoreCase = true) shouldBe false
        segs("def").isDescendentOf(segs("def"), ignoreCase = true) shouldBe false
    }

    @Test fun `segment isChildOf`() {
        emptyList<String>().isChildOf(emptyList()) shouldBe false
        null.isChildOf(segs("abc", "def", "ghi")) shouldBe false
        segs("abc", "def", "ghi").isChildOf(null) shouldBe false
        segs("abc", "def").isChildOf(segs("abc")) shouldBe true
        segs("abc", "def", "ghi").isChildOf(segs("abc")) shouldBe false
        segs("abc", "def", "ghi").isChildOf(segs("abc", "def")) shouldBe true
        segs("ABC", "def").isChildOf(segs("abc")) shouldBe false
        segs("ABC", "def").isChildOf(segs("abc"), ignoreCase = true) shouldBe true
    }

    @Test fun `segment contains`() {
        emptyList<String>().containsSegments(emptyList()) shouldBe true
        segs("abc", "def", "ghi").containsSegments(segs("abc", "def", "ghi")) shouldBe true
        segs("abc", "def", "ghi").containsSegments(segs("abc", "def")) shouldBe true
        segs("abc", "def", "ghi").containsSegments(segs("def")) shouldBe true
        segs("abc", "DEF", "ghi").containsSegments(segs("def")) shouldBe false
        segs("abc", "DEF", "ghi").containsSegments(segs("def"), ignoreCase = true) shouldBe true

        segs("abc", "def", "ghi").containsSegments(segs("c", "def", "g"), allowPartial = false) shouldBe false
        segs("abc", "def", "ghi").containsSegments(segs("c", "def", "g"), allowPartial = true) shouldBe true

        segs("abc", "def", "ghi").containsSegments(segs("c", "def", ""), allowPartial = false) shouldBe false
        segs("abc", "def", "ghi").containsSegments(segs("c", "def", ""), allowPartial = true) shouldBe true

        segs("abc", "def").containsSegments(segs("abc", ""), allowPartial = true) shouldBe true
        segs("abc", "def").containsSegments(segs("abc", ""), allowPartial = false) shouldBe false
        segs("abc", "def").containsSegments(segs("def", ""), allowPartial = true) shouldBe false

        segs("abc", "DEF", "ghi").containsSegments(
            segs("c", "def", "g"),
            ignoreCase = false,
            allowPartial = true
        ) shouldBe false
        segs("abc", "DEF", "ghi").containsSegments(
            segs("c", "def", "g"),
            ignoreCase = true,
            allowPartial = true
        ) shouldBe true

        segs("abc", "def", "ghi").containsSegments(segs("def"), allowPartial = false) shouldBe true
        segs("abc", "def", "ghi").containsSegments(segs("ef"), allowPartial = false) shouldBe false
        segs("abc", "def", "ghi").containsSegments(segs("ef"), allowPartial = true) shouldBe true
    }

    @Test fun `segment startsWith`() {
        emptyList<String>().startsWith(emptyList()) shouldBe true
        null.startsWith(segs("abc", "def")) shouldBe false
        segs("abc", "def").startsWith(null) shouldBe false

        segs("abc", "def").startsWith(segs("abc", "def")) shouldBe true
        segs("abc", "def").startsWith(segs("abc", "de"), allowPartial = false) shouldBe false
        segs("abc", "def").startsWith(segs("abc", "de"), allowPartial = true) shouldBe true
        segs("abc", "def").startsWith(segs("abc")) shouldBe true
        segs("abc", "def").startsWith(segs("ab"), allowPartial = false) shouldBe false
        segs("abc", "def").startsWith(segs("ab"), allowPartial = true) shouldBe true

        segs("ABc", "def").startsWith(segs("abc", "def")) shouldBe false
        segs("ABc", "def").startsWith(segs("abc", "def"), ignoreCase = true) shouldBe true

        segs("ABc", "def").startsWith(segs("abc", "de")) shouldBe false
        segs("ABc", "def").startsWith(segs("abc", "de"), ignoreCase = true, allowPartial = false) shouldBe false
        segs("ABc", "def").startsWith(segs("abc", "de"), ignoreCase = true, allowPartial = true) shouldBe true

        segs("ABc", "def").startsWith(segs("abc")) shouldBe false
        segs("ABc", "def").startsWith(segs("abc"), ignoreCase = true) shouldBe true

        segs("ABc", "def").startsWith(segs("ab")) shouldBe false
        segs("ABc", "def").startsWith(segs("ab"), ignoreCase = true, allowPartial = false) shouldBe false
        segs("ABc", "def").startsWith(segs("ab"), ignoreCase = true, allowPartial = true) shouldBe true
    }

    @Test fun `segment endsWith`() {
        emptyList<String>().endsWith(emptyList()) shouldBe true
        null.endsWith(segs("abc", "def")) shouldBe false
        segs("abc", "def").endsWith(null) shouldBe false

        segs("abc", "def").endsWith(segs("abc", "def")) shouldBe true
        segs("123", "abc", "def").endsWith(segs("abc", "def")) shouldBe true
        segs("", "abc", "def").endsWith(segs("abc", "def")) shouldBe true
        segs("", "123", "abc", "def").endsWith(segs("abc", "def")) shouldBe true
        segs("", "sdcard", "abc", "def").endsWith(segs("abc", "def")) shouldBe true

        segs("abc", "def").endsWith(segs("bc", "def"), allowPartial = false) shouldBe false
        segs("abc", "def").endsWith(segs("bc", "def"), allowPartial = true) shouldBe true
        segs("abc", "def").endsWith(segs("def")) shouldBe true
        segs("abc", "def").endsWith(segs("ef"), allowPartial = false) shouldBe false
        segs("abc", "def").endsWith(segs("ef"), allowPartial = true) shouldBe true
        segs("", "123", "abc", "def").endsWith(segs("c", "def"), allowPartial = true) shouldBe true

        segs("abc", "dEF").endsWith(segs("abc", "def")) shouldBe false
        segs("abc", "dEF").endsWith(segs("abc", "def"), ignoreCase = true) shouldBe true

        segs("abc", "dEF").endsWith(segs("bc", "def")) shouldBe false
        segs("abc", "dEF").endsWith(segs("def"), ignoreCase = true) shouldBe true

        segs("abc", "dEF").endsWith(segs("def")) shouldBe false
        segs("abc", "dEF").endsWith(segs("def"), ignoreCase = true) shouldBe true

        segs("abc", "dEF").endsWith(segs("ef")) shouldBe false
        segs("abc", "dEF").endsWith(segs("ef"), ignoreCase = true) shouldBe false
        segs("abc", "dEF").endsWith(segs("ef"), allowPartial = true) shouldBe false
        segs("abc", "dEF").endsWith(segs("ef"), ignoreCase = true, allowPartial = true) shouldBe true
    }
}
