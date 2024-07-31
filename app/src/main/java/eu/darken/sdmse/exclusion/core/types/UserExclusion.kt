package eu.darken.sdmse.exclusion.core.types

data class UserExclusion(
    override val exclusion: Exclusion,
) : ExclusionHolder, Exclusion by exclusion