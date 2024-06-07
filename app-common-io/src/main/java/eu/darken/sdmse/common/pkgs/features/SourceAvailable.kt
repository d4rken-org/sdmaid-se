package eu.darken.sdmse.common.pkgs.features

import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.local.LocalPath

interface SourceAvailable : Installed {

    val sourceDir: APath?
        get() = applicationInfo?.sourceDir?.let { LocalPath.build(it) }

    val splitSources: Set<APath>?
        get() = applicationInfo?.splitSourceDirs?.map { LocalPath.build(it) }?.toSet()

}