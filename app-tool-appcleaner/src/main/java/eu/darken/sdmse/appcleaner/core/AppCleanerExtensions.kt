package eu.darken.sdmse.appcleaner.core

val AppCleaner.Data?.hasData: Boolean
    get() = this?.junks?.isNotEmpty() ?: false

/**
 * Like [hasData], but ignoring junks that are [AppJunk.isUnclearable]. This is what delete
 * actions should key on: unclearable junks would make them report "0 deleted" forever.
 */
val AppCleaner.Data?.hasActionableData: Boolean
    get() = this?.junks?.any { !it.isUnclearable } ?: false

/**
 * Whether the snapshot still contains a target that would make a whole-tool clean reach the ADB
 * cache trim. Exclusion-limited junks don't count, otherwise the condition could satisfy itself.
 *
 * Pinned by `AppCleanerTest.a snapshot whose last trim-eligible junk is gone drops its
 * exclusion-limited entries`.
 */
fun Collection<AppJunk>.hasTrimEligibleTargets(): Boolean =
    any { !it.isExclusionLimited && it.inaccessibleCache != null }

/**
 * An exclusion-limited junk may only exist alongside a junk that keeps the trim reachable, e.g.
 * a snapshot of [limited(excluded app), normal(no inaccessible cache)] becomes [normal].
 */
fun Collection<AppJunk>.pruneOrphanedExclusionLimited(): Collection<AppJunk> =
    if (hasTrimEligibleTargets()) this else filter { !it.isExclusionLimited }
