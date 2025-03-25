package eu.darken.sdmse.common.sieve

import eu.darken.sdmse.common.files.toSegs
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class CriteriaOperatortest : BaseTest() {

    @Test
    fun `and operator - names`() = runTest {
        val operator = CriteriaOperator.And(
            NameCriterium("abc", mode = NameCriterium.Mode.Start()),
            NameCriterium("ghi", mode = NameCriterium.Mode.End())
        )
        operator.match("seg/path/abc".toSegs()) shouldBe false
        operator.match("seg/abcghi/abc".toSegs()) shouldBe false
        operator.match("seg/path/abcghi".toSegs()) shouldBe true
    }

    @Test
    fun `or operator - names`() = runTest {
        val operator = CriteriaOperator.Or(
            NameCriterium("abc", mode = NameCriterium.Mode.Start()),
            NameCriterium("ghi", mode = NameCriterium.Mode.End())
        )
        operator.match("seg/abc/nope".toSegs()) shouldBe false
        operator.match("seg/ghi/nope".toSegs()) shouldBe false
        operator.match("seg/path/abc".toSegs()) shouldBe true
        operator.match("seg/path/ghi".toSegs()) shouldBe true
        operator.match("seg/path/abcghi".toSegs()) shouldBe true
    }

    @Test
    fun `and operator - segments`() = runTest {
        val operator = CriteriaOperator.And(
            SegmentCriterium("seg/abc", mode = SegmentCriterium.Mode.Start()),
            SegmentCriterium("ghi", mode = SegmentCriterium.Mode.End())
        )
        operator.match("seg/path/abc".toSegs()) shouldBe false
        operator.match("seg/abc/abcghi/abc".toSegs()) shouldBe false
        operator.match("seg/abc/ghi/abc".toSegs()) shouldBe false
        operator.match("seg/abc/ghi".toSegs()) shouldBe true
    }

    @Test
    fun `and operator - segments2`() = runTest {
        val operator = CriteriaOperator.And(
            SegmentCriterium("seg/abc", mode = SegmentCriterium.Mode.Start()),
            SegmentCriterium("ghi", mode = SegmentCriterium.Mode.Contain()),
        )
        operator.match("seg/path/abc".toSegs()) shouldBe false
        operator.match("seg/abc/ghi/abc".toSegs()) shouldBe true
        operator.match("seg/abc/ghi".toSegs()) shouldBe true
    }

    @Test
    fun `or operator - segments`() = runTest {
        val operator = CriteriaOperator.Or(
            SegmentCriterium("seg/abc", mode = SegmentCriterium.Mode.Start()),
            SegmentCriterium("ghi", mode = SegmentCriterium.Mode.End())
        )
        operator.match("abc/nope".toSegs()) shouldBe false
        operator.match("seg/abc/nope".toSegs()) shouldBe true
        operator.match("nope/ghi".toSegs()) shouldBe true
        operator.match("seg/abc/ghi".toSegs()) shouldBe true
    }

}