package eu.darken.sdmse.common.forensics.csi

import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.CSIProcessor
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.isInstalled

interface LocalCSIProcessor : CSIProcessor {

    suspend fun PkgRepo.isInstalled(owner: Owner): Boolean = this.isInstalled(owner.pkgId, owner.userHandle)

    override suspend fun findOwners(areaInfo: AreaInfo): CSIProcessor.Result {
        require(hasJurisdiction(areaInfo.type)) { "Wrong jurisdiction: ${areaInfo.type}" }
        return CSIProcessor.Result()
    }
}