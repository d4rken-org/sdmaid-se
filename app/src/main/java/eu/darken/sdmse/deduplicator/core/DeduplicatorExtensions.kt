package eu.darken.sdmse.deduplicator.core

val Deduplicator.Data?.hasData: Boolean
    get() = this?.groups?.isNotEmpty() ?: false