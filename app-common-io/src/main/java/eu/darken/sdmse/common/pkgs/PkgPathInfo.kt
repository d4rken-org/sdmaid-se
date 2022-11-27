package eu.darken.sdmse.common.pkgs

import eu.darken.sdmse.common.files.core.APath

data class PkgPathInfo(
    val packageName: String,
    val publicPrimary: APath,
    val publicSecondary: Collection<APath>
)