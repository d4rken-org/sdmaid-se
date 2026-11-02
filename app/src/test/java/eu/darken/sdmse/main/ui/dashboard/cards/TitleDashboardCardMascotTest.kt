package eu.darken.sdmse.main.ui.dashboard.cards

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

/**
 * Long-pressing the title mascot shows the full-screen mascot until the finger lifts. A held finger
 * still produces small moves, and the title item is rebuilt whenever task or upgrade state changes,
 * so neither may end the hold early. A density change restarts the gesture handler mid-hold without
 * delivering a release or cancel event, and that must not leave the overlay up.
 */
class TitleDashboardCardMascotTest : BaseComposeRobolectricTest() {

    private val events = mutableListOf<Boolean>()
    private val triggered: Boolean? get() = events.lastOrNull()

    private fun item(
        isWorking: Boolean = false,
        sink: MutableList<Boolean> = events,
    ) = TitleDashboardCardItem(
        upgradeInfo = null,
        isWorking = isWorking,
        onRibbonClicked = {},
        webpageTool = mockk(relaxed = true),
        onMascotTriggered = { sink.add(it) },
    )

    private var currentItem by mutableStateOf(item())
    private var density by mutableStateOf(Density(1f))

    private fun setContent() {
        composeRule.setContent {
            PreviewWrapper {
                CompositionLocalProvider(LocalDensity provides density) {
                    TitleDashboardCard(item = currentItem)
                }
            }
        }
    }

    private fun holdPastLongPress() {
        composeRule.onNodeWithTag(TITLE_MASCOT_TEST_TAG).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveBy(Offset(0f, 0f))
        }
        composeRule.waitForIdle()
    }

    private fun jitter() {
        composeRule.onNodeWithTag(TITLE_MASCOT_TEST_TAG).performTouchInput {
            moveBy(Offset(1f, 1f))
            moveBy(Offset(-1f, 0f))
        }
        composeRule.waitForIdle()
    }

    private fun release() {
        composeRule.onNodeWithTag(TITLE_MASCOT_TEST_TAG).performTouchInput { up() }
        composeRule.waitForIdle()
    }

    @Test
    fun `hold survives finger movement and release hides`() {
        setContent()

        holdPastLongPress()
        triggered shouldBe true

        jitter()
        triggered shouldBe true

        release()
        triggered shouldBe false
    }

    @Test
    fun `title item rebuilt mid-hold does not end the hold`() {
        setContent()

        holdPastLongPress()
        triggered shouldBe true

        val rebuiltEvents = mutableListOf<Boolean>()
        currentItem = item(isWorking = true, sink = rebuiltEvents)
        composeRule.waitForIdle()
        jitter()
        triggered shouldBe true

        release()
        events shouldBe listOf(true)
        rebuiltEvents shouldBe listOf(false)
    }

    @Test
    fun `gesture handler reset mid-hold hides the overlay`() {
        setContent()

        holdPastLongPress()
        triggered shouldBe true

        density = Density(2f)
        composeRule.waitForIdle()
        triggered shouldBe false
    }

    @Test
    fun `tap does not trigger the overlay`() {
        setContent()

        composeRule.onNodeWithTag(TITLE_MASCOT_TEST_TAG).performTouchInput {
            down(center)
            up()
        }
        composeRule.waitForIdle()

        events shouldNotContain true
    }
}
