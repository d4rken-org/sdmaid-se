package eu.darken.sdmse.automation.core.errors

import eu.darken.sdmse.common.error.causes

/**
 * Whether this is (or was caused by) the accessibility service being unable to run because the
 * screen was off or locked, e.g. during an unattended scheduled run. That's a by-design limitation
 * rather than a genuine failure, so callers can surface it with its own wording.
 */
fun Throwable.isScreenUnavailable(): Boolean =
    this is ScreenUnavailableException || causes.any { it is ScreenUnavailableException }
