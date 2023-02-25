package eu.darken.sdmse.exclusion.core

import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.pkgs.Pkg

sealed interface Exclusion {

    val tags: Collection<Tag>

    enum class Tag {
        GENERAL,
        CORPSEFINDER,
        SYSTEMCLEANER,
        APPCLEANER
    }

    interface Package : Exclusion {
        val pkgId: Pkg.Id
        suspend fun match(candidate: Pkg.Id): Boolean
    }

    interface Path : Exclusion {
        val path: APath
        suspend fun match(candidate: APath): Boolean
    }
}