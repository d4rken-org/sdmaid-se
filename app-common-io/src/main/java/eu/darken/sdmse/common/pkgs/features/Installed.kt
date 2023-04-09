package eu.darken.sdmse.common.pkgs.features

import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.user.UserHandle2

interface Installed : PkgInfo {

    val userHandle: UserHandle2

    val sourceDir: APath?
        get() = packageInfo.applicationInfo?.sourceDir?.let { LocalPath.build(it) }

}