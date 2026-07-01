package eu.darken.sdmse.automation.core

import eu.darken.sdmse.common.pkgs.Pkg
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take

/**
 * Emits exactly once when the user appears to have left the automated app for the home/launcher
 * screen, i.e. the foreground became a [homePkgs] package and stayed there for [graceMs].
 *
 * Used on Android TV where the on-screen Cancel button is unreachable by D-pad: pressing the
 * Home button surfaces the launcher, which we treat as an intentional cancel.
 *
 * Semantics:
 * - **Latched, non-resetting**: the first home sighting commits to a cancel after [graceMs]. We do
 *   NOT reset if the target app reappears mid-grace, because that reappearance is almost always the
 *   automation engine re-launching Settings — honoring the user's Home press is the intent.
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
    .filter { it != null && it.name in homePkgs }
    .take(1)
    .onEach { delay(graceMs) }
    .map { }
