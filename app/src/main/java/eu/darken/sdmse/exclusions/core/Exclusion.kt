package eu.darken.sdmse.exclusions.core

import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.pkgs.Pkg

interface Exclusion {

    val tags: Collection<Tag>

    enum class Tag {
        GENERAL,
        CORPSEFINDER,
        SYSTEMCLEANER,
        APPCLEANER
    }

    interface Package : Exclusion {
        suspend fun match(id: Pkg.Id): Boolean
    }

    interface Path : Exclusion {
        suspend fun match(aPath: APath): Boolean
    }
}