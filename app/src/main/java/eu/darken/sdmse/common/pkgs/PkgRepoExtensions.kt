package eu.darken.sdmse.common.pkgs

import eu.darken.sdmse.common.pkgs.features.Installed
import kotlinx.coroutines.flow.first


suspend fun PkgRepo.currentPkgs(): Collection<Installed> = this.pkgs.first()