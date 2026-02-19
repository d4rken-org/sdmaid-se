package eu.darken.sdmse.common.forensics.csi

import eu.darken.sdmse.common.clutter.Marker
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.Owner


fun Marker.Match.toOwners(areaInfo: AreaInfo) = this.packageNames.map {
    Owner(
        pkgId = it,
        flags = flags,
        userHandle = areaInfo.userHandle,
    )
}