package eu.darken.sdmse.appcleaner.core.automation.specs.aosp

import eu.darken.sdmse.automation.core.common.ACSNodeInfo
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import kotlinx.coroutines.delay

/**
 * Whether input focus went past the button row to a Settings node entirely above [anchor].
 * A node of another package is not past the row, that stays the focus guard's veto.
 *
 * ```
 * Button "Search settings"       [948,120 - 1080,264] <- input focus after RIGHT
 * entity_header_content (anchor) [42,300 - 1038,702]
 * ```
 * Focused bottom 264 <= anchor top 300: `true`.
 *
 * Only identifies the rightmost landing when DOWN started on the header with input focus confirmed there.
 */
internal fun ACSNodeInfo.landedPastRow(anchor: ACSNodeInfo): Boolean =
    packageName?.toString() == AOSPSpecs.SETTINGS_PKG.name && isOutsideButtonRow(anchor)

internal suspend fun awaitPastRowLanding(
    readPastRow: suspend () -> Boolean,
    polls: Int,
    pollDelayMs: Long,
): Boolean {
    repeat(polls) {
        delay(pollDelayMs)
        if (readPastRow()) return true
    }
    return false
}

internal class RightmostLandingOps(
    val bootstrapAnchorFocused: suspend () -> Boolean,
    val dpadDown: suspend () -> Boolean,
    val focusVetoesCenter: suspend () -> Boolean,
    val dpadCenter: suspend () -> Boolean,
)

/**
 * DOWN from the header landed on "Clear cache" and RIGHT went past it: back to the header,
 * DOWN once more, then CENTER.
 */
internal suspend fun pressFromRightmostLanding(
    tag: String,
    ops: RightmostLandingOps,
    stepDelayMs: Long,
): Boolean {
    val source = RIGHTMOST_LANDING_SOURCE

    val bootstrapped = ops.bootstrapAnchorFocused()
    log(tag, INFO) { "DPAD bootstrap input focus confirmed=$bootstrapped (source=$source)" }
    if (!bootstrapped) return false

    val steppedDown = ops.dpadDown()
    log(tag, INFO) { "DPAD_DOWN result=$steppedDown (source=$source)" }
    if (!steppedDown) return false
    delay(stepDelayMs)

    val veto = ops.focusVetoesCenter()
    log(tag, INFO) { "DPAD focus guard veto=$veto (source=$source)" }
    if (veto) {
        log(tag, WARN) { "DPAD_CENTER skipped, focus outside the button row (source=$source)" }
        return false
    }

    val clicked = ops.dpadCenter()
    log(tag, INFO) { "DPAD_CENTER result=$clicked (source=$source)" }
    return clicked
}

internal const val RIGHT_PROBE_POLLS = 5
internal const val RIGHT_PROBE_POLL_DELAY_MS = 100L
internal const val RIGHTMOST_LANDING_SOURCE = "quick-try-rightmost"
