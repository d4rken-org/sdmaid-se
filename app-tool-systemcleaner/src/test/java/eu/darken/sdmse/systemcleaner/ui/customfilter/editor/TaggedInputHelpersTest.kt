package eu.darken.sdmse.systemcleaner.ui.customfilter.editor

import eu.darken.sdmse.common.compose.icons.ApproximatelyEqual
import eu.darken.sdmse.common.compose.icons.Contain
import eu.darken.sdmse.common.compose.icons.ContainEnd
import eu.darken.sdmse.common.compose.icons.ContainStart
import eu.darken.sdmse.common.compose.icons.SdmIcons
import eu.darken.sdmse.common.sieve.NameCriterium
import eu.darken.sdmse.common.sieve.SegmentCriterium
import eu.darken.sdmse.common.sieve.SieveCriterium
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class TaggedInputHelpersTest : BaseTest() {

    @Test
    fun `inputTextToChipTag for SEGMENTS yields SegmentCriterium with Contain allowPartial`() {
        val criterium = inputTextToChipTag("storage/Pictures", TagType.SEGMENTS)

        val seg = criterium.shouldBeInstanceOf<SegmentCriterium>()
        seg.segments shouldBe listOf("storage", "Pictures")
        val mode = seg.mode.shouldBeInstanceOf<SegmentCriterium.Mode.Contain>()
        mode.allowPartial shouldBe true
    }

    @Test
    fun `inputTextToChipTag for NAME yields NameCriterium with Contain default`() {
        val criterium = inputTextToChipTag("receipt", TagType.NAME)

        val name = criterium.shouldBeInstanceOf<NameCriterium>()
        name.name shouldBe "receipt"
        name.mode.shouldBeInstanceOf<NameCriterium.Mode.Contain>()
    }

    @Test
    fun `criteriumValue surfaces the raw text for both kinds`() {
        criteriumValue(NameCriterium("receipt", NameCriterium.Mode.Contain())) shouldBe "receipt"
        criteriumValue(
            SegmentCriterium(listOf("storage", "Pictures"), SegmentCriterium.Mode.Contain(allowPartial = true)),
        ) shouldBe "storage/Pictures"
    }

    @Test
    fun `criteriumIcon maps each NameCriterium mode`() {
        criteriumIcon(NameCriterium("x", NameCriterium.Mode.Start())) shouldBe SdmIcons.ContainStart
        criteriumIcon(NameCriterium("x", NameCriterium.Mode.Contain())) shouldBe SdmIcons.Contain
        criteriumIcon(NameCriterium("x", NameCriterium.Mode.End())) shouldBe SdmIcons.ContainEnd
        criteriumIcon(NameCriterium("x", NameCriterium.Mode.Equal())) shouldBe SdmIcons.ApproximatelyEqual
    }

    @Test
    fun `criteriumIcon maps each supported SegmentCriterium mode`() {
        criteriumIcon(
            SegmentCriterium(listOf("x"), SegmentCriterium.Mode.Start(allowPartial = true)),
        ) shouldBe SdmIcons.ContainStart
        criteriumIcon(
            SegmentCriterium(listOf("x"), SegmentCriterium.Mode.Contain(allowPartial = true)),
        ) shouldBe SdmIcons.Contain
        criteriumIcon(
            SegmentCriterium(listOf("x"), SegmentCriterium.Mode.End(allowPartial = true)),
        ) shouldBe SdmIcons.ContainEnd
        criteriumIcon(
            SegmentCriterium(listOf("x"), SegmentCriterium.Mode.Equal()),
        ) shouldBe SdmIcons.ApproximatelyEqual
    }

    @Test
    fun `availableModesFor exposes 4 modes in canonical order per kind`() {
        val nameModes = availableModesFor(NameCriterium("x", NameCriterium.Mode.Contain()))
        nameModes.size shouldBe 4
        nameModes[0].first.shouldBeInstanceOf<NameCriterium.Mode.Start>()
        nameModes[1].first.shouldBeInstanceOf<NameCriterium.Mode.Contain>()
        nameModes[2].first.shouldBeInstanceOf<NameCriterium.Mode.End>()
        nameModes[3].first.shouldBeInstanceOf<NameCriterium.Mode.Equal>()

        val segModes = availableModesFor(
            SegmentCriterium(listOf("x"), SegmentCriterium.Mode.Contain(allowPartial = true)),
        )
        segModes.size shouldBe 4
        segModes[0].first.shouldBeInstanceOf<SegmentCriterium.Mode.Start>()
        segModes[1].first.shouldBeInstanceOf<SegmentCriterium.Mode.Contain>()
        segModes[2].first.shouldBeInstanceOf<SegmentCriterium.Mode.End>()
        segModes[3].first.shouldBeInstanceOf<SegmentCriterium.Mode.Equal>()
    }

    @Test
    fun `withMode preserves the criterium kind and swaps only the mode`() {
        val name = NameCriterium("x", NameCriterium.Mode.Contain())
        val swapped = withMode(name, NameCriterium.Mode.Equal()) as NameCriterium
        swapped.name shouldBe "x"
        swapped.mode.shouldBeInstanceOf<NameCriterium.Mode.Equal>()

        val seg = SegmentCriterium(listOf("a", "b"), SegmentCriterium.Mode.Contain(allowPartial = true))
        val swapped2 = withMode(seg, SegmentCriterium.Mode.End(allowPartial = false)) as SegmentCriterium
        swapped2.segments shouldBe listOf("a", "b")
        swapped2.mode.shouldBeInstanceOf<SegmentCriterium.Mode.End>()
    }

    private fun n(text: String, mode: NameCriterium.Mode = NameCriterium.Mode.Contain()): SieveCriterium =
        NameCriterium(text, mode)

    @Test
    fun `swapPreservingOrder swaps in place at the start middle and end of the set`() {
        val a = n("a")
        val b = n("b")
        val c = n("c")
        val newB = n("b2")
        val newA = n("a2")
        val newC = n("c2")
        val source = linkedSetOf<SieveCriterium>(a, b, c)

        swapPreservingOrder(source, a, newA).toList() shouldContainExactly listOf(newA, b, c)
        swapPreservingOrder(source, b, newB).toList() shouldContainExactly listOf(a, newB, c)
        swapPreservingOrder(source, c, newC).toList() shouldContainExactly listOf(a, b, newC)
    }

    @Test
    fun `swapPreservingOrder drops new when it equals an existing entry at lower index`() {
        val a = n("a")
        val b = n("b")
        val c = n("c")
        val source = linkedSetOf<SieveCriterium>(a, b, c)

        // Replace c -> b (b already at index 1, lower than c). Result drops the second copy.
        val result = swapPreservingOrder(source, c, b).toList()
        result shouldContainExactly listOf(a, b)
    }

    @Test
    fun `swapPreservingOrder returns set unchanged when old is not present`() {
        val a = n("a")
        val b = n("b")
        val ghost = n("ghost")
        val replacement = n("replacement")
        val source = linkedSetOf<SieveCriterium>(a, b)

        swapPreservingOrder(source, ghost, replacement).toList() shouldContainExactly listOf(a, b)
    }

    @Test
    fun `swapPreservingOrder handles null and empty sets`() {
        swapPreservingOrder(null, n("a"), n("a2")) shouldBe emptySet()
        swapPreservingOrder(emptySet(), n("a"), n("a2")) shouldBe emptySet()
    }
}
