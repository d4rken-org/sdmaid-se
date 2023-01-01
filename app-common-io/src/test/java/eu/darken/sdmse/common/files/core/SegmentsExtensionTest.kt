package eu.darken.sdmse.common.files.core

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class SegmentsExtensionTest : BaseTest() {

    @Test fun `test segment matches`() {
        emptyList<String>().matches(emptyList()) shouldBe true
        null.matches(listOf("abc", "def")) shouldBe false
        listOf("abc", "def").matches(null) shouldBe false
        listOf("abc", "def").matches(listOf("abc", "def")) shouldBe true
        listOf("abc", "DEF").matches(listOf("abc", "def")) shouldBe false
        listOf("abc", "DEF").matches(listOf("abc", "def"), ignoreCase = true) shouldBe true
    }

    @Test fun `test segment isAncestorOf`() {
        emptyList<String>().isAncestorOf(emptyList()) shouldBe false
        null.isAncestorOf(listOf("abc", "def")) shouldBe false
        listOf("abc", "def").isAncestorOf(null) shouldBe false
        listOf("abc").isAncestorOf(listOf("abc", "def")) shouldBe true
        listOf("ABC").isAncestorOf(listOf("abc", "def")) shouldBe false
        listOf("ABC").isAncestorOf(listOf("abc", "def"), ignoreCase = true) shouldBe true
    }

    @Test fun `test segment contains`() {
        emptyList<String>().containsSegments(emptyList()) shouldBe true
        listOf("abc", "def", "ghi").containsSegments(listOf("abc", "def", "ghi")) shouldBe true
        listOf("abc", "def", "ghi").containsSegments(listOf("abc", "def")) shouldBe true
        listOf("abc", "def", "ghi").containsSegments(listOf("def")) shouldBe true
        listOf("abc", "DEF", "ghi").containsSegments(listOf("def")) shouldBe false
        listOf("abc", "DEF", "ghi").containsSegments(listOf("def"), ignoreCase = true) shouldBe true

        listOf("abc", "def", "ghi").containsSegments(listOf("c", "def", "g"), allowPartial = false) shouldBe false
        listOf("abc", "def", "ghi").containsSegments(listOf("c", "def", "g"), allowPartial = true) shouldBe true

        listOf("abc", "DEF", "ghi").containsSegments(
            listOf("c", "def", "g"),
            ignoreCase = false,
            allowPartial = true
        ) shouldBe false
        listOf("abc", "DEF", "ghi").containsSegments(
            listOf("c", "def", "g"),
            ignoreCase = true,
            allowPartial = true
        ) shouldBe true
    }

    @Test fun `test segment startsWith`() {
        emptyList<String>().startsWith(emptyList()) shouldBe true
        null.startsWith(listOf("abc", "def")) shouldBe false
        listOf("abc", "def").startsWith(null) shouldBe false

        listOf("abc", "def").startsWith(listOf("abc", "def")) shouldBe true
        listOf("abc", "def").startsWith(listOf("abc", "de")) shouldBe true
        listOf("abc", "def").startsWith(listOf("abc")) shouldBe true
        listOf("abc", "def").startsWith(listOf("ab")) shouldBe true

        listOf("ABc", "def").startsWith(listOf("abc", "def")) shouldBe false
        listOf("ABc", "def").startsWith(listOf("abc", "def"), ignoreCase = true) shouldBe true

        listOf("ABc", "def").startsWith(listOf("abc", "de")) shouldBe false
        listOf("ABc", "def").startsWith(listOf("abc", "de"), ignoreCase = true) shouldBe true

        listOf("ABc", "def").startsWith(listOf("abc")) shouldBe false
        listOf("ABc", "def").startsWith(listOf("abc"), ignoreCase = true) shouldBe true

        listOf("ABc", "def").startsWith(listOf("ab")) shouldBe false
        listOf("ABc", "def").startsWith(listOf("ab"), ignoreCase = true) shouldBe true
    }
}
