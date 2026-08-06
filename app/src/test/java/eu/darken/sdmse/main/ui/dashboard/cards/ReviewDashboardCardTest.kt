package eu.darken.sdmse.main.ui.dashboard.cards

import android.app.Activity
import android.content.Context
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.CompositionLocalProvider
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
 * body, dismiss, review) have to be one-shot: whichever the user hits first wins and the others
 * become no-ops. Otherwise a dismiss landing after a review overwrites the completed-review
 * bookkeeping with a snooze.
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

        cardBody().performSemanticsAction(SemanticsActions.OnClick)
        dismissButton().performClick()

        composeRule.runOnIdle {
            reviews shouldBe 1
            dismisses shouldBe 0
        }
    }

    @Test
    fun `a review tap without an activity does not consume the gate`() {
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
