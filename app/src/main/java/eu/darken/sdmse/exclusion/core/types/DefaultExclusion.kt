package eu.darken.sdmse.exclusion.core.types

data class DefaultExclusion(
    val reason: String,
    val exclusion: Exclusion,
) : Exclusion by exclusion