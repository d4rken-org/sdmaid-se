package eu.darken.sdmse.common.pkgs.container

import android.content.pm.PackageInfo
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.ReadableApk

data class ApkArchive(
    override val id: Pkg.Id,
    override val packageInfo: PackageInfo
) : Pkg, ReadableApk