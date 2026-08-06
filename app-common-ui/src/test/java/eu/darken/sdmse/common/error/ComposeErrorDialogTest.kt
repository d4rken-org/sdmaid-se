package eu.darken.sdmse.common.error

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.common.R
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.navigation.NavigationController
import eu.darken.sdmse.common.navigation.NavigationDestination
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

/**
 * The shared error dialog: an error that offers a way out gets its own action plus a dismiss,
 * everything else keeps the acknowledge-only shape, and a fix action that blows up must never take
 * the UI - or the dialog's exit - down with it.
 */
class ComposeErrorDialogTest : BaseComposeRobolectricTest() {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val dismissLabel: String get() = context.getString(R.string.general_dismiss_action)
    private val okLabel: String get() = context.getString(android.R.string.ok)

    private var dismissals = 0

    private enum class Dest : NavigationDestination { TARGET }

    private class TestError(
        private val fixLabel: String? = null,
        private val fixAction: ((Activity) -> Unit)? = null,
        private val fixRoute: NavigationDestination? = null,
        private val fixErrorMessage: String? = null,
        private val infoLabel: String? = null,
        private val infoAction: ((Activity) -> Unit)? = null,
    ) : Exception(ERROR_BODY), HasLocalizedError {
        override fun getLocalizedError() = LocalizedError(
            throwable = this,
            label = ERROR_TITLE.toCaString(),
            description = ERROR_BODY.toCaString(),
            fixActionLabel = fixLabel?.toCaString(),
            fixAction = fixAction,
            fixActionRoute = fixRoute,
            fixActionErrorMessage = fixErrorMessage?.toCaString(),
            infoActionLabel = infoLabel?.toCaString(),
            infoAction = infoAction,
        )
    }

    /**
     * Mirrors the host: the dialog is latched on the current error, so it only disappears when the
     * dispatch actually calls back through [ComposeErrorDialog]'s onDismiss.
     */
    private fun show(error: Throwable, navController: NavigationController? = null) {
        composeRule.setContent {
            var visible by remember { mutableStateOf(true) }
            PreviewWrapper {
                if (visible) {
                    ComposeErrorDialog(
                        throwable = error,
                        onDismiss = {
                            dismissals++
                            visible = false
                        },
                        navController = navController,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `a fixable error offers its action next to a dismiss`() {
        show(TestError(fixLabel = FIX_LABEL, fixAction = {}))

        composeRule.onNodeWithText(ERROR_TITLE).assertIsDisplayed()
        composeRule.onNodeWithText(FIX_LABEL).assertIsDisplayed()
        composeRule.onNodeWithText(dismissLabel).assertIsDisplayed()
        // Nothing is merely acknowledged here, the error offers a way out.
        composeRule.onAllNodesWithText(okLabel).assertCountEquals(0)
    }

    @Test
    fun `dismissing closes the dialog without running the fix action`() {
        var fixInvocations = 0
        show(TestError(fixLabel = FIX_LABEL, fixAction = { fixInvocations++ }))

        composeRule.onNodeWithText(dismissLabel).performClick()
        composeRule.waitForIdle()

        fixInvocations shouldBe 0
        dismissals shouldBe 1
        composeRule.onAllNodesWithText(FIX_LABEL).assertCountEquals(0)
    }

    @Test
    fun `an ordinary error keeps the acknowledge-only dialog`() {
        // The dialog backs every screen: the fix/dismiss pair must stay exclusive to errors that
        // actually carry a fix action.
        show(RuntimeException("something went wrong"))

        composeRule.onNodeWithText(okLabel).assertIsDisplayed()
        composeRule.onAllNodesWithText(dismissLabel).assertCountEquals(0)
        composeRule.onAllNodesWithText(FIX_LABEL).assertCountEquals(0)
    }

    @Test
    fun `a throwing fix action still closes the dialog`() {
        var invoked = false
        show(
            TestError(
                fixLabel = FIX_LABEL,
                fixAction = {
                    // Flag first: the assertion below has to distinguish "action ran and threw"
                    // from "action was never dispatched".
                    invoked = true
                    throw IllegalStateException("fix action exploded")
                },
            )
        )

        composeRule.onNodeWithText(FIX_LABEL).performClick()
        composeRule.waitForIdle()

        invoked shouldBe true
        // Exactly one dismissal: the throw must neither swallow it nor double it.
        dismissals shouldBe 1
        composeRule.onAllNodesWithText(FIX_LABEL).assertCountEquals(0)
    }

    @Test
    fun `a throwing fix action with its own message keeps the dialog open and shows it inline`() {
        // A Toast caps at 2 lines and clipped this kind of message; the dialog body has no cap.
        show(
            TestError(
                fixLabel = FIX_LABEL,
                fixAction = { throw IllegalStateException("fix action exploded") },
                fixErrorMessage = FIX_ERROR_MESSAGE,
            )
        )

        composeRule.onNodeWithText(FIX_LABEL).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(FIX_ERROR_MESSAGE).assertIsDisplayed()
        dismissals shouldBe 0
        // Not latched: the way out stays available while the message is shown.
        composeRule.onNodeWithText(dismissLabel).performClick()
        composeRule.waitForIdle()
        dismissals shouldBe 1
    }

    @Test
    fun `a throwing info action never borrows the fix action's failure message`() {
        // The failure copy belongs to the fix action's dispatch, not to the error: the info button
        // dispatches without one and must keep the plain log-then-dismiss behaviour.
        show(
            TestError(
                fixLabel = FIX_LABEL,
                fixAction = {},
                fixErrorMessage = FIX_ERROR_MESSAGE,
                infoLabel = INFO_LABEL,
                infoAction = { throw IllegalStateException("info action exploded") },
            )
        )

        composeRule.onNodeWithText(INFO_LABEL).performClick()
        composeRule.waitForIdle()

        dismissals shouldBe 1
        composeRule.onAllNodesWithText(FIX_ERROR_MESSAGE).assertCountEquals(0)
    }

    @Test
    fun `a throwing route navigation still closes the dialog`() {
        val navController = mockk<NavigationController>(relaxed = true).apply {
            every { goTo(any(), any(), any()) } throws IllegalStateException("NavigationController not initialized")
        }
        show(TestError(fixLabel = FIX_LABEL, fixRoute = Dest.TARGET), navController = navController)

        composeRule.onNodeWithText(FIX_LABEL).performClick()
        composeRule.waitForIdle()

        verify { navController.goTo(Dest.TARGET, null, false) }
        dismissals shouldBe 1
        composeRule.onAllNodesWithText(FIX_LABEL).assertCountEquals(0)
    }
}

private const val ERROR_TITLE = "Test error title"
private const val ERROR_BODY = "Test error description"
private const val FIX_LABEL = "Fix it"
private const val FIX_ERROR_MESSAGE = "Fixing it did not work"
private const val INFO_LABEL = "Tell me more"
