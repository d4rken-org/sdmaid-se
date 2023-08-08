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

        segs("abc", "def", "ghi").containsSegments(segs("ef"), allowPartial = false) shouldBe false
        segs("abc", "def", "ghi").containsSegments(segs("ef"), allowPartial = true) shouldBe true
    }

    @Test fun `segment startsWith`() {
        emptyList<String>().startsWith(emptyList()) shouldBe true
        null.startsWith(segs("abc", "def")) shouldBe false
        segs("abc", "def").startsWith(null) shouldBe false

        segs("abc", "def").startsWith(segs("abc", "def")) shouldBe true
        segs("abc", "def").startsWith(segs("abc", "de")) shouldBe true
        segs("abc", "def").startsWith(segs("abc")) shouldBe true
        segs("abc", "def").startsWith(segs("ab")) shouldBe true

        segs("ABc", "def").startsWith(segs("abc", "def")) shouldBe false
        segs("ABc", "def").startsWith(segs("abc", "def"), ignoreCase = true) shouldBe true

        segs("ABc", "def").startsWith(segs("abc", "de")) shouldBe false
        segs("ABc", "def").startsWith(segs("abc", "de"), ignoreCase = true) shouldBe true

        segs("ABc", "def").startsWith(segs("abc")) shouldBe false
        segs("ABc", "def").startsWith(segs("abc"), ignoreCase = true) shouldBe true

        segs("ABc", "def").startsWith(segs("ab")) shouldBe false
        segs("ABc", "def").startsWith(segs("ab"), ignoreCase = true) shouldBe true
    }
}
