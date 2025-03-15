package eu.darken.sdmse.common.pkgs.pkgops.ipc

import android.os.Parcelable
import eu.darken.sdmse.common.pkgs.features.InstallId
import kotlinx.parcelize.Parcelize

@Parcelize
data class RunningPackagesResult(
    val pkgs: Set<InstallId>,
) : Parcelable