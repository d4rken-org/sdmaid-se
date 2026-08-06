package eu.darken.sdmse.main.ui.dashboard.cards

import android.app.Activity
import android.content.Context
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.R
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

/**
 * The card only disappears once the next state emission arrives, so its three tap targets (card
 * body, dismiss, review) need a latch: a dismiss after a review would overwrite the
 * completed-review bookkeeping with a snooze, a review after a dismiss would re-open what the user
 * just closed. The latch is asymmetric — repeated review taps stay allowed, because a Play request
 * can fail without persisting anything, which leaves the card on screen and in need of a retry.
 */
class ReviewDashboardCardTest : BaseComposeRobolectricTest() {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private var reviews = 0
    private var dismisses = 0

    private val item = ReviewDashboardCardItem(
        onReview = { reviews++ },
        onDismiss = { dismisses++ },
    )

    private fun setContent(activity: Activity? = mockk<Activity>(relaxed = true)) {
        composeRule.setContent {
            PreviewWrapper {
                CompositionLocalProvider(LocalActivity provides activity) {
                    ReviewDashboardCard(item = item)
                }
            }
        }
    }

    private val cardVisible = mutableStateOf(true)

    // Mirrors the dashboard host: a LazyColumn keyed by the item's stable id, which is constant for
    // this card.
    private fun setLazyContent(activity: Activity? = mockk<Activity>(relaxed = true)) {
        composeRule.setContent {
            PreviewWrapper {
                CompositionLocalProvider(LocalActivity provides activity) {
                    LazyColumn {
                        if (cardVisible.value) {
                            item(key = item.stableId) { ReviewDashboardCard(item = item) }
                        }
                    }
                }
            }
        }
    }

    // The card merges its descendants, so the body text resolves to the clickable card itself.
    private fun cardBody() = composeRule.onNodeWithText(context.getString(R.string.review_app_body))

    private fun dismissButton() = composeRule.onNodeWithText(context.getString(R.string.review_app_dismiss_action))

    private fun reviewButton() = composeRule.onNodeWithText(context.getString(R.string.review_app_review_action))

    @Test
    fun `a dismissed card ignores a later review tap`() {
        setContent()

        dismissButton().performClick()
        composeRule.runOnIdle { dismisses shouldBe 1 }

        reviewButton().assertIsNotEnabled()
        reviewButton().performClick()
        cardBody().performSemanticsAction(SemanticsActions.OnClick)

        composeRule.runOnIdle {
            reviews shouldBe 0
            dismisses shouldBe 1
        }
    }

    @Test
    fun `a reviewed card ignores a later dismiss tap`() {
        setContent()

        reviewButton().performClick()
        composeRule.runOnIdle { reviews shouldBe 1 }

        dismissButton().assertIsNotEnabled()
        dismissButton().performClick()

        composeRule.runOnIdle {
            reviews shouldBe 1
            dismisses shouldBe 0
        }
    }

    @Test
    fun `a tap on the card body counts as a review`() {
        setContent()

        cardBody().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle { reviews shouldBe 1 }

        dismissButton().assertIsNotEnabled()
        dismissButton().performClick()

        composeRule.runOnIdle {
            reviews shouldBe 1
            dismisses shouldBe 0
        }
    }

    @Test
    fun `repeated review taps are not absorbed by the card`() {
        setContent()

        reviewButton().performClick()
        composeRule.runOnIdle { reviews shouldBe 1 }

        // A failed Play request persists nothing and leaves the card up, so the retry has to work.
        // Duplicates are the tool's problem, it holds a single-flight lock for exactly this.
        reviewButton().assertIsEnabled()
        reviewButton().performClick()

        composeRule.runOnIdle {
            reviews shouldBe 2
            dismisses shouldBe 0
        }
    }

    @Test
    fun `a card that left the list comes back unlatched`() {
        setLazyContent()

        dismissButton().performClick()
        composeRule.runOnIdle { dismisses shouldBe 1 }

        // A priority cycle (MOTD, update or setup card taking the slot) removes the item and re-adds
        // it later. The lazy host retains saveable state per item key, so a latch that survives
        // disposal comes back with the card and leaves it dead.
        cardVisible.value = false
        composeRule.waitForIdle()
        cardVisible.value = true
        composeRule.waitForIdle()

        dismissButton().assertIsEnabled()
        reviewButton().assertIsEnabled()

        cardBody().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle { reviews shouldBe 1 }
    }

    @Test
    fun `a review tap without an activity consumes neither latch`() {
        setContent(activity = null)

        reviewButton().assertIsNotEnabled()
        reviewButton().performClick()
        cardBody().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle { reviews shouldBe 0 }

        // Nothing was handed to the caller, so the card must still be dismissable
        dismissButton().assertIsEnabled()
        dismissButton().performClick()

        composeRule.runOnIdle { dismisses shouldBe 1 }
    }
}
