package eu.darken.sdmse.exclusions.core

import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.pkgs.Pkg

interface Exclusion {

    interface Package : Exclusion {
        suspend fun match(id: Pkg.Id)
    }

    interface Path : Exclusion {
        suspend fun match(aPath: APath): Boolean
    }
}