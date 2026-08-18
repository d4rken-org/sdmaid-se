package eu.darken.sdmse.main.ui.dashboard.cards

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.squeezer.core.tasks.SqueezerProcessTask
import eu.darken.sdmse.squeezer.R as SqueezerR
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

/**
 * The Squeezer card used to render neither progress nor a result: a full compression drains the
 * media list, which pops the tool screen before its snackbar shows, so the dashboard is where the
 * user actually sees what happened.
 */
class SqueezerDashboardCardTest : BaseComposeRobolectricTest() {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private var cancels = 0

    private val result = SqueezerProcessTask.Success(
        affectedSpace = 34 * 1024 * 1024L,
        affectedPaths = emptySet(),
        processedCount = 2,
    )

    private val progress = Progress.Data(
        primary = "Compressing images".toCaString(),
        secondary = "".toCaString(),
        count = Progress.Count.Counter(current = 1, max = 2),
    )

    private fun setContent(
        progress: Progress.Data? = null,
        result: SqueezerProcessTask.Result? = null,
    ) {
        composeRule.setContent {
            PreviewWrapper {
                SqueezerDashboardCard(
                    item = SqueezerDashboardCardItem(
                        data = null,
                        isInitializing = false,
                        progress = progress,
                        result = result,
                        onViewDetails = {},
                        onCancel = { cancels++ },
                    ),
                )
            }
        }
    }

    private val description get() = context.getString(SqueezerR.string.squeezer_explanation_short)

    private val resultPrimary get() = result.primaryInfo.get(context)

    private val resultSecondary get() = result.secondaryInfo.get(context)

    private fun cancelButton() = composeRule.onNodeWithText(context.getString(CommonR.string.general_cancel_action))

    private fun configureButton() =
        composeRule.onNodeWithText(context.getString(CommonR.string.general_configure_action))

    @Test
    fun `without progress or result the card shows its description`() {
        setContent()

        composeRule.onNodeWithText(description).assertIsDisplayed()
    }

    @Test
    fun `a process result replaces the description`() {
        setContent(result = result)

        composeRule.onNodeWithText(resultPrimary).assertIsDisplayed()
        composeRule.onNodeWithText(resultSecondary).assertIsDisplayed()
        composeRule.onNodeWithText(description).assertDoesNotExist()
    }

    @Test
    fun `the result appears once progress clears`() {
        val liveProgress = mutableStateOf<Progress.Data?>(progress)
        composeRule.setContent {
            PreviewWrapper {
                SqueezerDashboardCard(
                    item = SqueezerDashboardCardItem(
                        data = null,
                        isInitializing = false,
                        progress = liveProgress.value,
                        result = result,
                        onViewDetails = {},
                        onCancel = { cancels++ },
                    ),
                )
            }
        }

        composeRule.onNodeWithText(resultPrimary).assertDoesNotExist()

        liveProgress.value = null
        composeRule.waitForIdle()

        composeRule.onNodeWithText(resultPrimary).assertIsDisplayed()
        composeRule.onNodeWithText(resultSecondary).assertIsDisplayed()
    }

    @Test
    fun `progress takes precedence over a result`() {
        setContent(progress = progress, result = result)

        composeRule.onNodeWithText("Compressing images").assertIsDisplayed()
        composeRule.onNodeWithText(resultPrimary).assertDoesNotExist()
        composeRule.onNodeWithText(description).assertDoesNotExist()
    }

    @Test
    fun `a running operation swaps configure for cancel`() {
        setContent(progress = progress)

        cancelButton().assertIsDisplayed()
        configureButton().assertDoesNotExist()
    }

    @Test
    fun `without progress the card offers configure`() {
        setContent()

        configureButton().assertIsDisplayed()
        cancelButton().assertDoesNotExist()
    }

    @Test
    fun `tapping cancel while in progress cancels the operation`() {
        setContent(progress = progress)

        cancelButton().performClick()

        composeRule.runOnIdle { cancels shouldBe 1 }
    }
}
