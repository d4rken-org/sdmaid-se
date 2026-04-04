package eu.darken.sdmse.squeezer.core

val Squeezer.Data?.hasData: Boolean
    get() = this?.media?.isNotEmpty() ?: false
