package eu.darken.sdmse.appcleaner.core

val AppCleaner.Data?.hasData: Boolean
    get() = this?.junks?.isNotEmpty() ?: false

/**
 * Like [hasData], but ignoring junks that are [AppJunk.isUnclearable]. This is what delete
 * actions should key on: unclearable junks would make them report "0 deleted" forever.
 */
val AppCleaner.Data?.hasActionableData: Boolean
    get() = this?.junks?.any { !it.isUnclearable } ?: false
