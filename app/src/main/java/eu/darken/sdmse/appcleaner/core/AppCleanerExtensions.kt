package eu.darken.sdmse.appcleaner.core

val AppCleaner.Data?.hasData: Boolean
    get() = this?.junks?.isNotEmpty() ?: false