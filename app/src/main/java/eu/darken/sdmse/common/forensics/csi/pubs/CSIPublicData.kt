package eu.darken.sdmse.common.forensics.csi.pubs

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.areas.currentAreas
import eu.darken.sdmse.common.areas.hasFlags
import eu.darken.sdmse.common.clutter.ClutterRepo
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.CSIProcessor
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.forensics.csi.LocalCSIProcessor
import eu.darken.sdmse.common.forensics.csi.toOwners
import eu.darken.sdmse.common.getFirstDirElement
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.toPkgId
import java.io.File
import javax.inject.Inject

@Reusable
class CSIPublicData @Inject constructor(
    private val clutterRepo: ClutterRepo,
    private val pkgRepo: PkgRepo,
    private val areaManager: DataAreaManager,
) : LocalCSIProcessor {

    override suspend fun hasJurisdiction(type: DataArea.Type): Boolean = type == DataArea.Type.PUBLIC_DATA

    override suspend fun identifyArea(target: APath): AreaInfo? {
        var primary: DataArea? = null

        for (area in areaManager.currentAreas().filter { it.type == DataArea.Type.PUBLIC_DATA }) {
            if (area.hasFlags(DataArea.Flag.PRIMARY)) primary = area

            val base = area.path
            if (target.path.startsWith("${base.path}${File.separator}") && target.path != base.path) {
                return AreaInfo(
                    dataArea = area,
                    file = target,
                    prefix = "${base.path}${File.separator}",
                    isBlackListLocation = true
                )
            }
        }

        if (target.path.contains(PUBLIC_DATA) && primary != null) {
            var prefix: String? = null
            if (target.path.startsWith("${LEGACY_PATH.path}${File.separator}") && target.path != LEGACY_PATH.path) {
                prefix = LEGACY_PATH.path
            }
            if (prefix != null) {
                return AreaInfo(
                    dataArea = primary,
                    file = target,
                    prefix = "$prefix$PUBLIC_DATA",
                    isBlackListLocation = true,
                )
            }
        }

        return null
    }

    override suspend fun findOwners(areaInfo: AreaInfo): CSIProcessor.Result {
        val owners = mutableSetOf<Owner>()

        val dirNameAsPkg = areaInfo.prefixFreePath.getFirstDirElement()
        var hiddenDirAsPkg: String? = null

        if (pkgRepo.isInstalled(dirNameAsPkg.toPkgId())) {
            owners.add(Owner(dirNameAsPkg.toPkgId()))
        } else {
            hiddenDirAsPkg = tryCleanName(dirNameAsPkg)
            if (hiddenDirAsPkg != null && pkgRepo.isInstalled(hiddenDirAsPkg.toPkgId())) {
                owners.add(Owner(hiddenDirAsPkg.toPkgId()))
            }
        }

        if (owners.isEmpty()) {
            clutterRepo.match(areaInfo.type, dirNameAsPkg)
                .map { it.toOwners() }
                .flatten()
                .run { owners.addAll(this) }
        }

        // Fallback, no downside to assuming that dirname=pkgname for PUBLIC_DATA if there are no other owners
        if (owners.isEmpty()) {
            owners.add(Owner((hiddenDirAsPkg ?: dirNameAsPkg).toPkgId()))
        }

        return CSIProcessor.Result(
            owners = owners,
            hasKnownUnknownOwner = false
        )
    }


    private fun tryCleanName(name: String): String? = when {
        name.startsWith(".external.") -> {
            // rare modifier, seen it on my N5 with the .external.com.plexapp.android
            name.substring(10)
        }
        name.startsWith("_") || name.startsWith(".") -> {
            // usually used in public/Android/data to hide stuff from uninstall
            // some devs just don't know better
            name.substring(1)
        }
        else -> null
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: CSIPublicData): CSIProcessor
    }

    companion object {
        val TAG: String = logTag("CSI", "Public", "Data")
        private val LEGACY_PATH = File("/storage/emulated/legacy/")
        val PUBLIC_DATA = "/Android/data/".replace("/", File.separator)
    }
}