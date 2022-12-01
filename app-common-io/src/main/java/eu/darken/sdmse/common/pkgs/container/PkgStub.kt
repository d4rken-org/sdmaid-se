package eu.darken.sdmse.common.pkgs.container

import eu.darken.sdmse.common.pkgs.Pkg

data class PkgStub(override val id: Pkg.Id) : Pkg

fun Pkg.Id.toStub(): PkgStub = PkgStub(this)
