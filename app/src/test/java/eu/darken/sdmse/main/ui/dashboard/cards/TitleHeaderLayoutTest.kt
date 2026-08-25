package eu.darken.sdmse.main.ui.dashboard.cards

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

/**
 * The dashboard header centers the title column in the card and places the mascot to its left, so a
 * long randomized slogan can grow the column into the mascot's slot and the mascot then draws on
 * top of it.
 *
 * The invariant is about where the children land, not about text, so these drive
 * [TitleHeaderLayout] with fixed-size stand-ins: a mascot-sized box, a title box far wider than any
 * card, and an optional badge. Robolectric's stub text metrics never enter into it.
 */
class TitleHeaderLayoutTest : BaseComposeRobolectricTest() {

    private fun setLayout(
        width: Dp = CARD_WIDTH,
        withBadge: Boolean = false,
        badgeWidth: Dp = BADGE_WIDTH,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) {
        composeRule.setContent {
            PreviewWrapper {
                CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                    Box(modifier = Modifier.width(width)) {
                        TitleHeaderLayout(
                            modifier = Modifier.fillMaxWidth(),
                            mascot = {
                                Box(
                                    modifier = Modifier
                                        .testTag(TAG_MASCOT)
                                        .size(width = MASCOT_WIDTH, height = MASCOT_HEIGHT),
                                )
                            },
                            title = {
                                Box(
                                    modifier = Modifier
                                        .testTag(TAG_TITLE)
                                        .width(OVERSIZED_TITLE_WIDTH)
                                        .height(TITLE_HEIGHT),
                                )
                            },
                            ribbon = if (withBadge) {
                                {
                                    Box(
                                        modifier = Modifier
                                            .testTag(TAG_BADGE)
                                            .size(width = badgeWidth, height = BADGE_HEIGHT),
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `the title leaves the mascot its slot`() {
        setLayout()

        val mascot = bounds(TAG_MASCOT)
        val title = bounds(TAG_TITLE)

        assertNoOverlap(mascot, title)
        withClue("mascot $mascot and title $title must keep the inline gap") {
            (horizontalGap(mascot, title) >= INLINE_SPACING - ROUNDING_SLACK) shouldBe true
        }
        withClue("mascot $mascot must stay left of title $title") {
            (mascot.right <= title.left) shouldBe true
        }
    }

    @Test
    fun `the badge stays beside the title without overlapping it`() {
        setLayout(withBadge = true)

        val mascot = bounds(TAG_MASCOT)
        val title = bounds(TAG_TITLE)
        val badge = bounds(TAG_BADGE)

        assertNoOverlap(mascot, title)
        assertNoOverlap(title, badge)
        withClue("badge $badge must sit right of title $title") {
            (title.right <= badge.left) shouldBe true
        }
        withClue("badge $badge must stay on the title row $title, not stack below it") {
            (badge.top < title.bottom && title.top < badge.bottom) shouldBe true
        }
    }

    /**
     * The production badge is content-sized around the version name, so it can be wider than the
     * mascot and then wins the reservation's `maxOf`. The other badge cases stay below the mascot
     * width and never reach that branch.
     */
    @Test
    fun `a badge wider than the mascot still keeps its slot`() {
        setLayout(withBadge = true, badgeWidth = WIDE_BADGE_WIDTH)

        val mascot = bounds(TAG_MASCOT)
        val title = bounds(TAG_TITLE)
        val badge = bounds(TAG_BADGE)

        assertNoOverlap(mascot, title)
        assertNoOverlap(title, badge)
        withClue("badge $badge must sit right of title $title") {
            (title.right <= badge.left) shouldBe true
        }
        withClue("badge $badge must stay on the title row $title, not stack below it") {
            (badge.top < title.bottom && title.top < badge.bottom) shouldBe true
        }
    }

    @Test
    fun `RTL mirrors the mascot slot without overlap`() {
        setLayout(layoutDirection = LayoutDirection.Rtl)

        val mascot = bounds(TAG_MASCOT)
        val title = bounds(TAG_TITLE)

        assertNoOverlap(mascot, title)
        withClue("RTL mascot $mascot must sit right of title $title") {
            (title.right <= mascot.left) shouldBe true
        }
        withClue("mascot $mascot and title $title must keep the inline gap") {
            (horizontalGap(title, mascot) >= INLINE_SPACING - ROUNDING_SLACK) shouldBe true
        }
    }

    @Test
    fun `RTL mirrors the badge slot without overlap`() {
        setLayout(withBadge = true, layoutDirection = LayoutDirection.Rtl)

        val mascot = bounds(TAG_MASCOT)
        val title = bounds(TAG_TITLE)
        val badge = bounds(TAG_BADGE)

        assertNoOverlap(mascot, title)
        assertNoOverlap(title, badge)
        withClue("RTL mascot $mascot must sit right of title $title") {
            (title.right <= mascot.left) shouldBe true
        }
        withClue("RTL badge $badge must sit left of title $title") {
            (badge.right <= title.left) shouldBe true
        }
    }

    /**
     * [NARROW_WIDTH] is where the 96dp title floor starts winning over the reservation: below it the
     * mascot slot is no longer honoured and the overlap returns.
     */
    @Test
    fun `the narrow width boundary still keeps the slots apart`() {
        setLayout(width = NARROW_WIDTH)

        assertNoOverlap(bounds(TAG_MASCOT), bounds(TAG_TITLE))
    }

    private fun bounds(tag: String): DpRect = composeRule
        .onNodeWithTag(tag, useUnmergedTree = true)
        .getUnclippedBoundsInRoot()

    private fun assertNoOverlap(a: DpRect, b: DpRect) = withClue("$a must not overlap $b") {
        (a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom) shouldBe false
    }

    private fun horizontalGap(left: DpRect, right: DpRect): Dp = right.left - left.right

    companion object {
        private const val TAG_MASCOT = "mascot"
        private const val TAG_TITLE = "title"
        private const val TAG_BADGE = "badge"

        // The production mascot measures 96dp tall at a 1080/1920 aspect ratio.
        private val MASCOT_WIDTH = 54.dp
        private val MASCOT_HEIGHT = 96.dp
        private val BADGE_WIDTH = 40.dp
        private val WIDE_BADGE_WIDTH = 72.dp
        private val BADGE_HEIGHT = 44.dp
        private val TITLE_HEIGHT = 48.dp
        private val OVERSIZED_TITLE_WIDTH = 600.dp

        private val CARD_WIDTH = 312.dp
        private val NARROW_WIDTH = 228.dp
        private val INLINE_SPACING = 12.dp
        private val ROUNDING_SLACK = 1.dp
    }
}
