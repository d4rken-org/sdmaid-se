package eu.darken.sdmse.common.forensics.csi

import eu.darken.sdmse.common.clutter.Marker
import eu.darken.sdmse.common.forensics.Owner


fun Marker.Match.toOwners() = this.packageNames.map {
    Owner(
        pkgId = it,
        flags = flags,
    )
}