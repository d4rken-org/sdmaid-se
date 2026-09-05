package eu.darken.sdmse.swiper.ui.sessions.items

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpRect
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.swiper.core.SessionState
import eu.darken.sdmse.swiper.core.SwipeSession
import eu.darken.sdmse.swiper.core.Swiper
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest
import java.time.Instant

class SwiperSessionRowTest : BaseComposeRobolectricTest() {

    private fun unscannedSession(): Swiper.SessionWithStats = Swiper.SessionWithStats(
        session = SwipeSession(
            sessionId = "session-1",
            sourcePaths = listOf(LocalPath.build("storage", "emulated", "0", "DCIM")),
            currentIndex = 0,
            totalItems = 0,
            createdAt = Instant.parse("2025-01-01T00:00:00Z"),
            lastModifiedAt = Instant.parse("2025-01-01T00:00:00Z"),
            state = SessionState.CREATED,
        ),
        keepCount = 0,
        deleteCount = 0,
        undecidedCount = 0,
        deletedCount = 0,
        deleteFailedCount = 0,
    )

    private fun setRow(
        isScanning: Boolean = false,
        isRefreshing: Boolean = false,
    ) {
        composeRule.setContent {
            PreviewWrapper {
                SwiperSessionRow(
                    sessionWithStats = unscannedSession(),
                    position = 1,
                    isScanning = isScanning,
                    isCancelling = false,
                    isRefreshing = isRefreshing,
                    onScan = {},
                    onContinue = {},
                    onRemove = {},
                    onRename = {},
                    onCancel = {},
                    onFilter = {},
                    onSortOrder = {},
                )
            }
        }
    }

    private fun spinnerBounds(): DpRect = composeRule
        .onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
        .getBoundsInRoot()

    // Layout-level overlap check: Robolectric's text metrics are stubs, but node bounds come
    // from Compose's own measure/place pass, so two siblings intersecting is a real layout fact.
    private infix fun DpRect.overlaps(other: DpRect): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    @Test
    fun `scanning spinner does not overlap the cancel button`() {
        setRow(isScanning = true)

        val cancel = composeRule.onNodeWithText("Cancel").getBoundsInRoot()

        spinnerBounds() overlaps cancel shouldBe false
    }

    @Test
    fun `refreshing spinner does not overlap the loading button`() {
        setRow(isRefreshing = true)

        val loading = composeRule.onNodeWithText("Loading").getBoundsInRoot()

        spinnerBounds() overlaps loading shouldBe false
    }
}
