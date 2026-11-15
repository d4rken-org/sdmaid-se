package eu.darken.sdmse.appcleaner.core.automation.specs.aosp

import eu.darken.sdmse.automation.core.common.ACSNodeInfo
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class DpadRightmostLandingTest : BaseTest() {

    private fun node(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        viewId: String? = null,
        pkg: String? = SETTINGS,
    ): ACSNodeInfo = mockk<ACSNodeInfo>(relaxed = true).also { n ->
        every { n.viewIdResourceName } returns viewId
        every { n.packageName } returns pkg
        every { n.getScreenBounds() } returns ACSNodeInfo.ScreenBounds(left, top, right, bottom)
    }

    private fun anchor() = node(42, 300, 1038, 702, viewId = ENTITY_HEADER_CONTENT_ID)

    @Test
    fun `search button above the header is past the row`() {
        node(948, 120, 1080, 264).landedPastRow(anchor()) shouldBe true
    }

    @Test
    fun `the anchor itself is not past the row`() {
        val anchor = anchor()

        anchor.landedPastRow(anchor) shouldBe false
    }

    @Test
    fun `a node below the header is not past the row`() {
        node(42, 760, 520, 880).landedPastRow(anchor()) shouldBe false
    }

    @Test
    fun `a node overlapping the header is not past the row`() {
        node(84, 250, 996, 400).landedPastRow(anchor()) shouldBe false
    }

    @Test
    fun `a node of another package above the header is not past the row`() {
        node(948, 120, 1080, 264, pkg = "com.android.launcher3").landedPastRow(anchor()) shouldBe false
    }

    @Test
    fun `a past-row reading on the third poll ends the wait at 300ms`() = runTest {
        val readTimes = mutableListOf<Long>()

        val result = awaitPastRowLanding(
            readPastRow = {
                readTimes += testScheduler.currentTime
                readTimes.size == 3
            },
            polls = RIGHT_PROBE_POLLS,
            pollDelayMs = RIGHT_PROBE_POLL_DELAY_MS,
        )

        result shouldBe true
        readTimes shouldContainExactly listOf(100L, 200L, 300L)
        testScheduler.currentTime shouldBe 300L
    }

    @Test
    fun `no past-row reading gives up after five polls at 500ms`() = runTest {
        var reads = 0

        val result = awaitPastRowLanding(
            readPastRow = { reads++; false },
            polls = RIGHT_PROBE_POLLS,
            pollDelayMs = RIGHT_PROBE_POLL_DELAY_MS,
        )

        result shouldBe false
        reads shouldBe 5
        testScheduler.currentTime shouldBe 500L
    }

    @Test
    fun `no read happens before the first poll delay`() = runTest {
        val readTimes = mutableListOf<Long>()

        val job = launch {
            awaitPastRowLanding(
                readPastRow = { readTimes += testScheduler.currentTime; false },
                polls = RIGHT_PROBE_POLLS,
                pollDelayMs = RIGHT_PROBE_POLL_DELAY_MS,
            )
        }
        runCurrent()
        readTimes.shouldBeEmpty()
        advanceTimeBy(99)
        runCurrent()
        readTimes.shouldBeEmpty()

        job.join()
        readTimes.first() shouldBe 100L
    }

    private class Fake(
        var bootstrap: Boolean = true,
        var down: Boolean = true,
        var veto: Boolean = false,
        var center: Boolean = true,
    ) {
        val calls = mutableListOf<String>()

        val ops = RightmostLandingOps(
            bootstrapAnchorFocused = { calls += BOOTSTRAP; bootstrap },
            dpadDown = { calls += DOWN; down },
            focusVetoesCenter = { calls += GUARD; veto },
            dpadCenter = { calls += CENTER; center },
        )
    }

    private suspend fun Fake.press(): Boolean = pressFromRightmostLanding(
        tag = "test",
        ops = ops,
        stepDelayMs = 120,
    )

    @Test
    fun `happy path runs bootstrap, DOWN, guard and CENTER once in order`() = runTest {
        val fake = Fake()

        fake.press() shouldBe true

        fake.calls shouldContainExactly listOf(BOOTSTRAP, DOWN, GUARD, CENTER)
    }

    @Test
    fun `the CENTER result is the result`() = runTest {
        val fake = Fake(center = false)

        fake.press() shouldBe false

        fake.calls shouldContainExactly listOf(BOOTSTRAP, DOWN, GUARD, CENTER)
    }

    @Test
    fun `unconfirmed bootstrap sends no key`() = runTest {
        val fake = Fake(bootstrap = false)

        fake.press() shouldBe false

        fake.calls shouldContainExactly listOf(BOOTSTRAP)
    }

    @Test
    fun `failed DOWN sends no CENTER`() = runTest {
        val fake = Fake(down = false)

        fake.press() shouldBe false

        fake.calls shouldContainExactly listOf(BOOTSTRAP, DOWN)
    }

    @Test
    fun `guard veto sends no CENTER`() = runTest {
        val fake = Fake(veto = true)

        fake.press() shouldBe false

        fake.calls shouldContainExactly listOf(BOOTSTRAP, DOWN, GUARD)
    }

    companion object {
        private const val SETTINGS = "com.android.settings"

        private const val BOOTSTRAP = "BOOTSTRAP"
        private const val DOWN = "DOWN"
        private const val GUARD = "GUARD"
        private const val CENTER = "CENTER"
    }
}
