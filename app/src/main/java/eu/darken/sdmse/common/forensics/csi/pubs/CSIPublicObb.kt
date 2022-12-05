package eu.darken.sdmse.common.forensics.csi.pubs

import android.os.storage.StorageManager
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
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.files.core.GatewaySwitch
import eu.darken.sdmse.common.files.core.listFiles
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.CSIProcessor
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.forensics.csi.LocalCSIProcessor
import eu.darken.sdmse.common.getFirstDirElement
import eu.darken.sdmse.common.pkgs.PkgManager
import eu.darken.sdmse.common.pkgs.toPkgId
import java.io.File
import javax.inject.Inject

@Reusable
class CSIPublicObb @Inject constructor(
    private val clutterRepo: ClutterRepo,
    private val pkgManager: PkgManager,
    private val areaManager: DataAreaManager,
    private val storageManager: StorageManager,
    private val gatewaySwitch: GatewaySwitch,
) : LocalCSIProcessor {

    override suspend fun hasJurisdiction(type: DataArea.Type): Boolean = type == DataArea.Type.PUBLIC_OBB

    override suspend fun identifyArea(target: APath): AreaInfo? {
        var primary: DataArea? = null

        for (area in areaManager.currentAreas().filter { it.type == DataArea.Type.PUBLIC_OBB }) {
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

        if (target.path.contains(PUBLIC_OBB) && primary != null) {
            var prefix: String? = null
            if (target.path.startsWith("${LEGACY_PATH.path}${File.separator}") && target.path != LEGACY_PATH.path) {
                prefix = LEGACY_PATH.path
            }
            if (prefix != null) {
                return AreaInfo(
                    dataArea = primary,
                    file = target,
                    prefix = "$prefix$PUBLIC_OBB",
                    isBlackListLocation = true,
                )
            }
        }

        return null
    }

    override suspend fun findOwners(areaInfo: AreaInfo): CSIProcessor.Result {
        val owners = mutableSetOf<Owner>()
        var hasKnownUnknownOwner = false

        val dirNameAsPkg = areaInfo.prefixFreePath.getFirstDirElement()

        if (pkgManager.isInstalled(dirNameAsPkg.toPkgId())) {
            owners.add(Owner(dirNameAsPkg.toPkgId()))
        } else {
            clutterRepo.match(areaInfo.type, dirNameAsPkg)
                .map { it.toOwners() }
                .flatten()
                .run { owners.addAll(this) }
        }

        if (owners.isEmpty()) {
            try {
                val content = areaInfo.file.listFiles(gatewaySwitch)
                hasKnownUnknownOwner = content
                    .filterIsInstance<LocalPath>()
                    .any { storageManager.isObbMounted(it.path) }
            } catch (e: Exception) {
                log(TAG, ERROR) { "Path listing failed: ${e.asLog()}" }
            }
        }

        return CSIProcessor.Result(
            owners = owners,
            hasKnownUnknownOwner = hasKnownUnknownOwner
        )
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: CSIPublicObb): CSIProcessor
    }

    companion object {
        val TAG: String = logTag("CSI", "Public", "Obb")
        private val LEGACY_PATH = File("/storage/emulated/legacy/")
        val PUBLIC_OBB = "/Android/obb/".replace("/", File.separator)
    }
}