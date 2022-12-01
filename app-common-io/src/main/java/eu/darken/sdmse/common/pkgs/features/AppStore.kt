package eu.darken.sdmse.common.pkgs.features

import eu.darken.sdmse.common.pkgs.Pkg

interface AppStore : Pkg {

    val urlGenerator: ((Pkg.Id) -> String)?
        get() = null
}