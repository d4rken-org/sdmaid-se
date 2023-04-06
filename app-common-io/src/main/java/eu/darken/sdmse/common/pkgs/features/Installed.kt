package eu.darken.sdmse.common.pkgs.features

import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.local.LocalPath

interface Installed : PkgInfo {

    val sourceDir: APath?
        get() = packageInfo.applicationInfo?.sourceDir?.let { LocalPath.build(it) }

}