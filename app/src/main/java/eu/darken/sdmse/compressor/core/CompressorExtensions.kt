package eu.darken.sdmse.compressor.core

val Compressor.Data?.hasData: Boolean
    get() = this?.images?.isNotEmpty() ?: false
