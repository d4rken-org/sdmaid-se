package eu.darken.sdmse.exclusion.core.types

data class DefaultExclusion(
    val reason: String,
    override val exclusion: Exclusion,
) : ExclusionHolder, Exclusion by exclusion