package eu.darken.sdmse.common.forensics.csi

import eu.darken.sdmse.common.forensics.CSIProcessor
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.pkgs.PkgRepo

interface LocalCSIProcessor : CSIProcessor {

    suspend fun PkgRepo.isInstalled(owner: Owner): Boolean = isInstalled(owner.pkgId)

}