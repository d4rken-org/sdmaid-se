package eu.darken.sdmse.common.forensics.csi.dalvik.tools

import dagger.Reusable
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.forensics.csi.dalvik.DalvikCheck
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.pkgs.toPkgId
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@Reusable
class ApkCheck @Inject constructor(
    private val pkgOps: PkgOps
) : DalvikCheck {
    suspend fun check(areaInfo: AreaInfo, candidates: Collection<LocalPath>): DalvikCheck.Result {
        val userHandle = areaInfo.userHandle
        val owners = candidates
            .asFlow()
            .filter { it.name.endsWith(".apk") }
            .mapNotNull { pkgOps.viewArchive(it) }
            .onEach { log(TAG, VERBOSE) { "ApkInfo: $it" } }
            .map {
                if (it.id.name.startsWith("com.google.android.gms.")) {
                    /**
                     * /data/dalvik-cache/arm64/system@product@priv-app@PrebuiltGmsCore@app_chimera@m@PrebuiltGmsCoreRvc_DynamiteModulesC.apk@classes.vdex
                     * to
                     * /system/product/priv-app/PrebuiltGmsCore/app_chimera/m/PrebuiltGmsCoreRvc_DynamiteModulesC.apk
                     *
                     * /data/dalvik-cache/arm64/system@product@priv-app@PrebuiltGmsCore@m@independent@AndroidPlatformServices.apk@classes.dex
                     * to
                     * /system/product/priv-app/PrebuiltGmsCore/m/independent/AndroidPlatformServices.apk
                     */
                    setOf(Owner(it.id, userHandle), Owner("com.google.android.gms".toPkgId(), userHandle))
                } else {
                    setOf(Owner(it.id, userHandle))
                }
            }
            .firstOrNull()
            ?: emptySet()

        return DalvikCheck.Result(owners)
    }

    companion object {
        val TAG: String = logTag("CSI", "Dalvik", "Dex", "ApkCheck")
    }
}