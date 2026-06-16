package eu.darken.sdmse.common.compose.settings

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import eu.darken.sdmse.common.access.AccessState
import kotlinx.coroutines.launch

/**
 * UI-level resolution of whether a gated settings row is usable, still set-up-able, or a
 * genuine dead-end. Derived from the [AccessState]s of the privileged methods that can unlock
 * the feature (root and/or Shizuku).
 */
enum class FeatureGateState {
    /** Feature works right now — render a normal switch (no gate). */
    AVAILABLE,

    /** Not usable yet, but a setup path is open — render the actionable "set up" badge. */
    SETUP,

    /** Not usable and no path remains — render the non-actionable "unavailable" badge. */
    BLOCKED,
}

/**
 * OR-combine the unlocking methods: the feature is available if any method is [AccessState.Active];
 * still actionable while any method is [AccessState.Checking] or [AccessState.Undecided];
 * otherwise (all [AccessState.Declined]/[AccessState.Unavailable]) it's a dead-end.
 *
 * [AccessState.Checking] resolves to [SETUP] (not [BLOCKED]) so an in-flight probe never flashes
 * a premature dead-end.
 */
fun privilegedGateState(states: List<AccessState>): FeatureGateState = when {
    states.any { it == AccessState.Active } -> FeatureGateState.AVAILABLE
    states.any { it == AccessState.Checking || it == AccessState.Undecided } -> FeatureGateState.SETUP
    else -> FeatureGateState.BLOCKED
}

fun privilegedGateState(vararg states: AccessState): FeatureGateState = privilegedGateState(states.toList())

/**
 * Same as [privilegedGateState] but takes [available] — the tool's own authoritative
 * "feature works right now" flag (e.g. `useRoot`/`useAdb`-derived) — as the single source of
 * truth for [FeatureGateState.AVAILABLE]. [states] only decides SETUP vs BLOCKED when the feature
 * is not available. This keeps the gate consistent with the flag the rest of the tool (and the
 * actual task) uses, so they can't diverge (a row never becomes interactive while the capability
 * is off, and a working device never flashes BLOCKED from a slow privileged probe).
 */
fun privilegedGateState(available: Boolean, states: List<AccessState>): FeatureGateState = when {
    available -> FeatureGateState.AVAILABLE
    // Not available per the authoritative flag. BLOCKED only when every method is settled-negative
    // (Declined/Unavailable); a transient Active/Checking (probe ahead of the flag) stays SETUP so
    // we never flash a dead-end for a capability that's about to come online.
    states.all { it == AccessState.Declined || it == AccessState.Unavailable } -> FeatureGateState.BLOCKED
    else -> FeatureGateState.SETUP
}

fun FeatureGateState.toSettingGate(): SettingGate? = when (this) {
    FeatureGateState.AVAILABLE -> null
    FeatureGateState.SETUP -> SettingGate.SetupRequired
    FeatureGateState.BLOCKED -> SettingGate.Unavailable
}

/**
 * Click handler for a [SettingsBadgedSwitchItem] driven by a [FeatureGateState]:
 *  - [FeatureGateState.SETUP] → invoke [onSetup] (navigate to the setup screen)
 *  - [FeatureGateState.BLOCKED] → show [blockedMessage] in a snackbar (non-actionable dead-end)
 *  - [FeatureGateState.AVAILABLE] → no-op (the real switch handles its own toggle)
 */
@Composable
fun rememberGateClickHandler(
    snackbarHostState: SnackbarHostState,
): (gate: FeatureGateState, blockedMessage: String, onSetup: () -> Unit) -> Unit {
    val scope = rememberCoroutineScope()
    return remember(snackbarHostState, scope) {
        { gate, blockedMessage, onSetup ->
            when (gate) {
                FeatureGateState.SETUP -> onSetup()
                FeatureGateState.BLOCKED -> {
                    scope.launch { snackbarHostState.showSnackbar(blockedMessage) }
                    Unit
                }

                FeatureGateState.AVAILABLE -> Unit
            }
        }
    }
}
