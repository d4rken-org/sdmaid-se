package eu.darken.sdmse.appcleaner.core.automation.specs.aosp

/**
 * Manufacturers whose Settings withholds the clear-cache row from the accessibility tree, where
 * D-pad focus traversal is the only way to reach it.
 */
internal val DPAD_FALLBACK_MANUFACTURERS = setOf("google", "motorola")

internal fun supportsDpadFallback(manufacturer: String): Boolean =
    DPAD_FALLBACK_MANUFACTURERS.any { it.equals(manufacturer, ignoreCase = true) }
