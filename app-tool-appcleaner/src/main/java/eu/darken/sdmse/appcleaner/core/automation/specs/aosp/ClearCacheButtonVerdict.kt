package eu.darken.sdmse.appcleaner.core.automation.specs.aosp

internal enum class NoButtonVerdict {
    /** Not enough information yet, let the step keep polling. */
    KEEP_TRYING,

    /** Settings says the cache is empty, so there is nothing left for us to click. */
    ALREADY_EMPTY,

    /** There is cache, the page has rendered, and the button still isn't there. */
    NO_BUTTON,
}

/**
 * Decides what to do when the storage page is up but no clickable "Clear cache" was found.
 *
 * [cacheSize] is what Settings itself prints in the cache row, null when that could not be read or
 * parsed. Null must never be read as "not zero": it also covers the "calculating…" state.
 */
internal fun noButtonVerdict(
    cacheSize: Long?,
    isSystemApp: Boolean,
    useDpadFallback: Boolean,
): NoButtonVerdict = when {
    cacheSize == null -> NoButtonVerdict.KEEP_TRYING
    cacheSize == 0L -> NoButtonVerdict.ALREADY_EMPTY
    // The DPAD fallback exists because the button is deliberately invisible to us on those
    // devices, so its absence from the tree proves nothing until DPAD has had its turn.
    useDpadFallback -> NoButtonVerdict.KEEP_TRYING
    // Same verdict clickClearCache reaches when it does find a disabled button on a system app.
    // For a normal app a missing button is more likely a screen we haven't understood, so we
    // keep polling rather than declare it unclearable.
    isSystemApp -> NoButtonVerdict.NO_BUTTON
    else -> NoButtonVerdict.KEEP_TRYING
}

/**
 * Holds a terminal verdict back until the same one is reached twice in a row.
 *
 * Finding the button and reading the size are two separate crawls of a screen that may still be
 * settling, so a single observation can be stale in both directions: a button that renders a moment
 * later would be declared missing, and a row still showing a placeholder zero would be declared
 * empty. Requiring two passes costs about a second and means the button search ran again in
 * between, since each pass starts with it.
 */
internal class VerdictConfirmer {
    private var previous: NoButtonVerdict? = null

    fun confirm(verdict: NoButtonVerdict): NoButtonVerdict {
        val confirmed = verdict != NoButtonVerdict.KEEP_TRYING && verdict == previous
        previous = verdict
        return if (confirmed) verdict else NoButtonVerdict.KEEP_TRYING
    }
}
