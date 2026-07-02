package eu.darken.sdmse.automation.core

import eu.darken.sdmse.common.pkgs.Pkg
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.transform

/**
 * Emits when the user appears to have left the automated app for the home/launcher screen,
 * i.e. the foreground became a [homePkgs] package, after a [graceMs] settling delay.
 *
 * Used on Android TV where the on-screen Cancel button is unreachable by D-pad: pressing the
 * Home button surfaces the launcher, which we treat as an intentional cancel.
 *
 * Semantics:
 * - **Latched per sighting**: a home sighting commits to an emission after [graceMs]. It does NOT
 *   reset if the target app reappears mid-grace, because that reappearance is almost always the
 *   automation engine re-launching Settings — honoring the user's Home press is the intent.
 * - **Re-arming**: consumers may suppress an emission (e.g. the overlay wasn't armed yet, so the
 *   sighting was task-startup navigation rather than a user leave); every later home sighting
 *   emits again. A single-shot latch here would permanently disarm Home-to-cancel for the rest
 *   of a possibly multi-minute task while the overlay still promises it.
 * - **Null/foreign foreground ignored**: only home packages matter; everything else is dropped.
 *
 * Callers are expected to feed a stream already restricted to foreground-window changes
 * (`TYPE_WINDOW_STATE_CHANGED`) and to re-check that a cancel is still wanted (e.g. the overlay is
 * still armed) before acting.
 */
internal fun leaveSignals(
    foregroundPkgs: Flow<Pkg.Id?>,
    homePkgs: Set<String>,
    graceMs: Long,
): Flow<Unit> = foregroundPkgs
    // A single Home press can produce several window events for the launcher; they are one
    // sighting, not several (a suppressed first firing must not leave a queued duplicate behind
    // that then cancels without a second Home press).
    .distinctUntilChanged()
    .filter { it != null && it.name in homePkgs }
    // Sightings arriving while a grace delay is running collapse into at most one pending
    // emission — also keeps this slow collector from backpressuring the shared event stream.
    .conflate()
    .transform {
        delay(graceMs)
        emit(Unit)
    }
