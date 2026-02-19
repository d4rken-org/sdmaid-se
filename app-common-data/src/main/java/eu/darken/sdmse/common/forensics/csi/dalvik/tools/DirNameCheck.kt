package eu.darken.sdmse.common.forensics.csi.dalvik.tools

import dagger.Reusable
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.forensics.csi.dalvik.DalvikCheck
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.isInstalled
import eu.darken.sdmse.common.pkgs.toPkgId
import javax.inject.Inject

@Reusable
class DirNameCheck @Inject constructor(
    private val pkgRepo: PkgRepo,
) : DalvikCheck {

    suspend fun process(areaInfo: AreaInfo): DalvikCheck.Result {
        val potPkg = areaInfo.prefixFreeSegments.first().toPkgId()
        return if (pkgRepo.isInstalled(potPkg, areaInfo.userHandle)) {
            DalvikCheck.Result(setOf(Owner(potPkg, areaInfo.userHandle)))
        } else {
            return DalvikCheck.Result()
        }
    }
}