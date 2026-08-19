package eu.darken.sdmse.stats.core

import eu.darken.sdmse.stats.core.forecast.StorageForecast

sealed interface LowSpaceAction {
    data class Notify(val forecast: StorageForecast, val freeBytes: Long) : LowSpaceAction
    data object Cancel : LowSpaceAction
    data object Nothing : LowSpaceAction
}

data class LowSpaceDecision(
    val action: LowSpaceAction,
    /** `null` leaves the transition latch untouched. */
    val armedAfter: Boolean?,
)

/**
 * Whether the low-space warning should speak, go quiet, or stay out of the way.
 *
 * The trigger is the FORECAST, not an "is low" flag. [StorageForecast.BelowFloor] is returned the
 * moment free space reaches the threshold, so triggering on "is low" would make the predictive copy
 * unreachable and every warning would arrive after the fact.
 *
 * [LowSpaceAction.Cancel] ALWAYS re-arms. It means "no notification is standing", so the next
 * qualifying condition has to be able to speak: an off/on of the toggle, or losing and regaining
 * Pro, must not leave the user silently un-warned while their storage keeps filling. A successful
 * post is the only thing that spends the latch, which is why [LowSpaceDecision.armedAfter] is left
 * to the caller on the notify path.
 */
object LowSpaceAlertDecider {

    fun decide(
        enabled: Boolean,
        isPro: Boolean,
        armed: Boolean,
        forecast: StorageForecast?,
        freeBytes: Long,
    ): LowSpaceDecision {
        if (!enabled || !isPro) return LowSpaceDecision(LowSpaceAction.Cancel, armedAfter = true)

        val isWarning = when (forecast) {
            is StorageForecast.Filling -> forecast.isUrgent
            StorageForecast.BelowFloor -> true
            else -> false
        }
        if (!isWarning || forecast == null) return LowSpaceDecision(LowSpaceAction.Cancel, armedAfter = true)

        // Warning state, but the latch is spent: stay quiet without clearing what is on screen.
        if (!armed) return LowSpaceDecision(LowSpaceAction.Nothing, armedAfter = null)

        // The caller sets armedAfter from the post result: false on POSTED, unchanged otherwise.
        return LowSpaceDecision(LowSpaceAction.Notify(forecast, freeBytes), armedAfter = null)
    }
}
