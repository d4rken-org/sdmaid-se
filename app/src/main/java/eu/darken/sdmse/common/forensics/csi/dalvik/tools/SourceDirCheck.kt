package eu.darken.sdmse.common.forensics.csi.dalvik.tools

import dagger.Reusable
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.forensics.csi.dalvik.DalvikCheck
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.currentPkgs
import javax.inject.Inject

@Reusable
class SourceDirCheck @Inject constructor(
    private val pkgRepo: PkgRepo,
) : DalvikCheck {

    suspend fun check(areaInfo: AreaInfo, candidates: Collection<LocalPath>): DalvikCheck.Result {
        val ownerPkg = pkgRepo.currentPkgs()
            .filter { it.sourceDir != null }
            .firstOrNull { pkg ->
                candidates.any { it.path == pkg.sourceDir?.path }

            }

        return DalvikCheck.Result(
            owners = setOfNotNull(ownerPkg?.id?.let { Owner(it, areaInfo.userHandle) })
        )
    }
}