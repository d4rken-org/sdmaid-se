package eu.darken.sdmse.common.pkgs

import eu.darken.sdmse.common.pkgs.features.Installed

interface PkgDataSource {
    suspend fun getPkgs(): Collection<Installed>
}