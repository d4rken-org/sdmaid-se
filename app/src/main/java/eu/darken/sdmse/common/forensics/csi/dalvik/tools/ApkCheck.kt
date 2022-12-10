package eu.darken.sdmse.common.forensics.csi.dalvik.tools

import dagger.Reusable
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.forensics.csi.dalvik.DalvikCheck
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.pkgs.toPkgId
import javax.inject.Inject

@Reusable
class ApkCheck @Inject constructor(
    private val pkgOps: PkgOps
) : DalvikCheck {
    suspend fun check(candidates: Collection<LocalPath>): DalvikCheck.Result {
        val owners = candidates
            .filter { it.name.endsWith(".apk") }
            .mapNotNull {
                pkgOps.viewArchive(it)?.id
            }
            .map {
                if (it.name.startsWith("com.google.android.gms.")) {
                    /**
                     * /data/dalvik-cache/arm64/system@product@priv-app@PrebuiltGmsCore@app_chimera@m@PrebuiltGmsCoreRvc_DynamiteModulesC.apk@classes.vdex
                     * to
                     * /system/product/priv-app/PrebuiltGmsCore/app_chimera/m/PrebuiltGmsCoreRvc_DynamiteModulesC.apk
                     *
                     * /data/dalvik-cache/arm64/system@product@priv-app@PrebuiltGmsCore@m@independent@AndroidPlatformServices.apk@classes.dex
                     * to
                     * /system/product/priv-app/PrebuiltGmsCore/m/independent/AndroidPlatformServices.apk
                     */
                    setOf(Owner(it), Owner("com.google.android.gms".toPkgId()))
                } else {
                    setOf(Owner(it))
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