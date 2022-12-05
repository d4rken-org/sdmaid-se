package eu.darken.sdmse.common.forensics.csi

import eu.darken.sdmse.common.clutter.Marker
import eu.darken.sdmse.common.forensics.CSIProcessor
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.pkgs.PkgManager

interface LocalCSIProcessor : CSIProcessor {

    suspend fun PkgManager.isInstalled(owner: Owner): Boolean = isInstalled(owner.pkgId)

    fun Marker.Match.toOwners() = this.packageNames.map {
        Owner(
            pkgId = it,
            flags = flags,
        )
    }
}