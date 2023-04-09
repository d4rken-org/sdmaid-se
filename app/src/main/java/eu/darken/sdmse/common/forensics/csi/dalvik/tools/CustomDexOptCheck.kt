package eu.darken.sdmse.common.forensics.csi.dalvik.tools

import dagger.Reusable
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.forensics.csi.dalvik.DalvikCheck
import eu.darken.sdmse.common.pathChopOffLast
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.currentPkgs
import java.io.File
import javax.inject.Inject

@Reusable
class CustomDexOptCheck @Inject constructor(
    private val pkgRepo: PkgRepo,
) : DalvikCheck {

    suspend fun check(
        areaInfo: AreaInfo,
    ): Pair<DalvikCheck.Result, LocalPath?> {
        val owners = mutableSetOf<Owner>()
        val currentPkgs = pkgRepo.currentPkgs()
        var extraPathToCheck: LocalPath? = null

        // Custom apk/jar subfile that has been optimized manually
        // https://android.googlesource.com/platform/libcore-snapshot/+/ics-mr1/dalvik/src/main/java/dalvik/system/DexFile.java
        if (areaInfo.file.name.contains("@")) {
            var trunk: String? = File.separator + areaInfo.file.name.replace("@", File.separator)
            var firstSlice: String? = null
            while (trunk != null) {
                val slicePOI = trunk.lastIndexOf(File.separator)
                if (slicePOI != -1 && slicePOI < trunk.length) {
                    val poi = trunk.substring(slicePOI + 1, trunk.length)
                    if (poi.isEmpty()) continue

                    val hit = currentPkgs.firstOrNull { it.id.name == poi }
                    if (hit != null) {
                        owners.add(Owner(hit.id, areaInfo.userHandle))
                        break
                    }
                }

                trunk = trunk.pathChopOffLast()
                if (firstSlice == null) {
                    // We expect this to be a packagename
                    // e.g. data@app@ ^ eu.thedarken.sdm-1.apk ^ @classes.dex
                    firstSlice = trunk
                }
            }
            if (owners.isEmpty() && firstSlice != null) {
                extraPathToCheck = LocalPath.build(firstSlice)
            }
        }
        return DalvikCheck.Result(owners) to extraPathToCheck
    }
}