package eu.darken.sdmse.systemcleaner.core

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class FilterContentTest : BaseTest() {

    @Test
    fun `FilterContent size sums per-item lookup size`() {
        val fc = fakeFilterContent(
            items = listOf(
                fakeMatch(name = "a", size = 100L),
                fakeMatch(name = "b", size = 250L),
                fakeMatch(name = "c", size = 4L),
            ),
        )
        fc.size shouldBe 354L
    }

    @Test
    fun `FilterContent size is zero when items is empty`() {
        fakeFilterContent(items = emptyList()).size shouldBe 0L
    }

    @Test
    fun `Data totalSize sums across all filter contents`() {
        val data = SystemCleaner.Data(
            filterContents = listOf(
                fakeFilterContent(identifier = "f1", items = listOf(fakeMatch(name = "a", size = 100L))),
                fakeFilterContent(identifier = "f2", items = listOf(fakeMatch(name = "b", size = 50L), fakeMatch(name = "c", size = 25L))),
            ),
        )
        data.totalSize shouldBe 175L
    }

    @Test
    fun `Data totalCount sums items across all filter contents`() {
        val data = SystemCleaner.Data(
            filterContents = listOf(
                fakeFilterContent(identifier = "f1", items = List(3) { fakeMatch(name = "a$it") }),
                fakeFilterContent(identifier = "f2", items = List(2) { fakeMatch(name = "b$it") }),
            ),
        )
        data.totalCount shouldBe 5
    }

    @Test
    fun `Data totalSize is zero for empty filterContents`() {
        SystemCleaner.Data(filterContents = emptyList()).totalSize shouldBe 0L
    }
}
