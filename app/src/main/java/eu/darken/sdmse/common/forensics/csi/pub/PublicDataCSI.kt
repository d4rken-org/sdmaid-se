package eu.darken.sdmse.common.forensics.csi.pub

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
import eu.darken.sdmse.common.files.core.containsSegments
import eu.darken.sdmse.common.files.core.isAncestorOf
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.CSIProcessor
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.forensics.csi.LocalCSIProcessor
import eu.darken.sdmse.common.forensics.csi.pub.SdcardCSI.Companion.LEGACY_PATH
import eu.darken.sdmse.common.forensics.csi.toOwners
import eu.darken.sdmse.common.getFirstDirElement
import eu.darken.sdmse.common.pkgs.toPkgId
import java.io.File
import javax.inject.Inject

@Reusable
class PublicDataCSI @Inject constructor(
    private val clutterRepo: ClutterRepo,
    private val pkgRepo: eu.darken.sdmse.common.pkgs.PkgRepo,
    private val areaManager: DataAreaManager,
) : LocalCSIProcessor {

    override suspend fun hasJurisdiction(type: DataArea.Type): Boolean = type == DataArea.Type.PUBLIC_DATA

    override suspend fun identifyArea(target: APath): AreaInfo? {
        var primary: DataArea? = null

        for (area in areaManager.currentAreas().filter { it.type == DataArea.Type.PUBLIC_DATA }) {
            if (area.hasFlags(DataArea.Flag.PRIMARY)) primary = area

            if (area.path.isAncestorOf(target)) {
                return AreaInfo(
                    dataArea = area,
                    file = target,
                    prefix = "${area.path.path}${File.separator}",
                    isBlackListLocation = true
                )
            }
        }

        if (target.containsSegments(*BASE_SEGMENTS) && primary != null && LEGACY_PATH.isAncestorOf(target)) {
            return AreaInfo(
                dataArea = primary,
                file = target,
                prefix = LEGACY_PATH.child(*BASE_SEGMENTS).path,
                isBlackListLocation = true,
            )
        }

        return null
    }

    override suspend fun findOwners(areaInfo: AreaInfo): CSIProcessor.Result {
        require(hasJurisdiction(areaInfo.type)) { "Wrong jurisdiction: ${areaInfo.type}" }

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
        @Binds @IntoSet abstract fun mod(mod: PublicDataCSI): CSIProcessor
    }

    companion object {
        val TAG: String = logTag("CSI", "Public", "Data")
        val BASE_SEGMENTS = arrayOf("Android", "data")
    }
}