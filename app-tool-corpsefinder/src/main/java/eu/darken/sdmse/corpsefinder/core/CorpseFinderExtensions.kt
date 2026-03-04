package eu.darken.sdmse.corpsefinder.core

val CorpseFinder.Data?.hasData: Boolean
    get() = this?.corpses?.isNotEmpty() ?: false