package eu.darken.sdmse.stats.core

import eu.darken.sdmse.stats.core.forecast.StorageForecast
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class LowSpaceAlertDeciderTest : BaseTest() {

    private val free = 1_000_000_000L
    private val urgent = StorageForecast.Filling(daysUntilFloor = 3, bytesPerDay = 1_000_000_000L, isUrgent = true)
    private val calm = StorageForecast.Filling(daysUntilFloor = 90, bytesPerDay = 1_000_000L, isUrgent = false)

    private fun decide(
        enabled: Boolean = true,
        isPro: Boolean = true,
        armed: Boolean = true,
        forecast: StorageForecast? = urgent,
    ) = LowSpaceAlertDecider.decide(
        enabled = enabled,
        isPro = isPro,
        armed = armed,
        forecast = forecast,
        freeBytes = free,
    )

    // ─────────────────────────── notify ───────────────────────────

    @Test
    fun `an urgent Filling forecast notifies with that forecast`() {
        // Regression guard: forecast() returns BelowFloor the moment free space reaches the
        // threshold, so a trigger on "is low" would make this branch unreachable.
        val decision = decide(forecast = urgent)

        val notify = decision.action.shouldBeInstanceOf<LowSpaceAction.Notify>()
        notify.forecast shouldBe urgent
        notify.freeBytes shouldBe free
        // The caller sets the latch from the post result.
        decision.armedAfter shouldBe null
    }

    @Test
    fun `BelowFloor notifies`() {
        val decision = decide(forecast = StorageForecast.BelowFloor)

        val notify = decision.action.shouldBeInstanceOf<LowSpaceAction.Notify>()
        notify.forecast shouldBe StorageForecast.BelowFloor
        decision.armedAfter shouldBe null
    }

    // ─────────────────────────── nothing to say ───────────────────────────

    @Test
    fun `a non-urgent Filling forecast cancels and re-arms`() {
        decide(forecast = calm) shouldBe LowSpaceDecision(LowSpaceAction.Cancel, armedAfter = true)
    }

    @Test
    fun `Stable cancels and re-arms`() {
        decide(forecast = StorageForecast.Stable) shouldBe LowSpaceDecision(LowSpaceAction.Cancel, armedAfter = true)
    }

    @Test
    fun `Erratic cancels and re-arms`() {
        decide(forecast = StorageForecast.Erratic) shouldBe LowSpaceDecision(LowSpaceAction.Cancel, armedAfter = true)
    }

    @Test
    fun `InsufficientData cancels and re-arms`() {
        decide(forecast = StorageForecast.InsufficientData) shouldBe
                LowSpaceDecision(LowSpaceAction.Cancel, armedAfter = true)
    }

    @Test
    fun `a missing forecast cancels and re-arms`() {
        decide(forecast = null) shouldBe LowSpaceDecision(LowSpaceAction.Cancel, armedAfter = true)
    }

    // ─────────────────────────── gates ───────────────────────────

    @Test
    fun `a disabled toggle cancels and re-arms even in the warning band`() {
        decide(enabled = false) shouldBe LowSpaceDecision(LowSpaceAction.Cancel, armedAfter = true)
    }

    @Test
    fun `a non-Pro user cancels and re-arms even in the warning band`() {
        decide(isPro = false) shouldBe LowSpaceDecision(LowSpaceAction.Cancel, armedAfter = true)
    }

    @Test
    fun `recovery while the toggle is off re-arms`() {
        // Latch already spent, feature switched off, storage recovered: the next crossing must
        // still be able to speak.
        decide(enabled = false, armed = false, forecast = StorageForecast.Stable) shouldBe
                LowSpaceDecision(LowSpaceAction.Cancel, armedAfter = true)
    }

    @Test
    fun `recovery while non-Pro re-arms`() {
        decide(isPro = false, armed = false, forecast = StorageForecast.Stable) shouldBe
                LowSpaceDecision(LowSpaceAction.Cancel, armedAfter = true)
    }

    // ─────────────────────────── latch ───────────────────────────

    @Test
    fun `a spent latch stays quiet without clearing the standing notification`() {
        decide(armed = false) shouldBe LowSpaceDecision(LowSpaceAction.Nothing, armedAfter = null)
    }

    @Test
    fun `re-enabling while still in the warning band does not re-notify a spent latch`() {
        // Off: cancel + re-arm... but the latch was already spent and the OFF pass re-armed it,
        // so this models the case where the user toggles off and on again in one warning episode.
        val off = decide(enabled = false, armed = false)
        off.armedAfter shouldBe true

        // Simulate the caller NOT having written the re-arm yet (e.g. the DataStore write lost a
        // race): the latch is still spent, so nothing is posted.
        decide(enabled = true, armed = false) shouldBe LowSpaceDecision(LowSpaceAction.Nothing, armedAfter = null)
    }

    @Test
    fun `Pro loss then regain lets the warning speak again`() {
        // Warned once, latch spent.
        decide(armed = true).action.shouldBeInstanceOf<LowSpaceAction.Notify>()

        // Pro lapses: cancel + re-arm.
        val lost = decide(isPro = false, armed = false)
        lost.action shouldBe LowSpaceAction.Cancel
        lost.armedAfter shouldBe true

        // Pro returns with the storage still in the warning band.
        decide(isPro = true, armed = true).action.shouldBeInstanceOf<LowSpaceAction.Notify>()
    }
}
