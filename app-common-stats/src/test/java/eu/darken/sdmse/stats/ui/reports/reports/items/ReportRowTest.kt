package eu.darken.sdmse.stats.ui.reports.items

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.stats.core.Report
import eu.darken.sdmse.stats.ui.reports.ReportsViewModel
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest
import java.time.Instant
import java.util.UUID

class ReportRowTest : BaseComposeRobolectricTest() {

    private fun partialRow(errorMessage: String?) = ReportsViewModel.Row(
        reportId = UUID.randomUUID(),
        tool = SDMTool.Type.APPCLEANER,
        status = Report.Status.PARTIAL_SUCCESS,
        endAt = Instant.now(),
        primaryMessage = "46 expendable items deleted",
        secondaryMessage = null,
        errorMessage = errorMessage,
    )

    @Test
    fun `a partial row shows what was done and what went wrong`() {
        composeRule.setContent {
            PreviewWrapper {
                ReportRow(
                    row = partialRow("The screen was turned off or locked"),
                    now = Instant.now(),
                    onClick = {},
                )
            }
        }

        composeRule.onNodeWithText("46 expendable items deleted").assertIsDisplayed()
        composeRule.onNodeWithText("The screen was turned off or locked").assertIsDisplayed()
    }

    @Test
    fun `a partial row without an error shows no error line`() {
        composeRule.setContent {
            PreviewWrapper {
                ReportRow(
                    row = partialRow(errorMessage = null),
                    now = Instant.now(),
                    onClick = {},
                )
            }
        }

        composeRule.onNodeWithText("46 expendable items deleted").assertIsDisplayed()
        composeRule.onNodeWithText("The screen was turned off or locked").assertDoesNotExist()
    }
}
