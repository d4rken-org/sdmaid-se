package eu.darken.sdmse.common.forensics.csi.pub

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
import eu.darken.sdmse.common.files.core.*
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.CSIProcessor
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.forensics.csi.LocalCSIProcessor
import eu.darken.sdmse.common.forensics.csi.pub.SdcardCSI.Companion.LEGACY_PATH
import eu.darken.sdmse.common.forensics.csi.toOwners
import eu.darken.sdmse.common.pkgs.toPkgId
import javax.inject.Inject

@Reusable
class PublicObbCSI @Inject constructor(
    private val clutterRepo: ClutterRepo,
    private val pkgRepo: eu.darken.sdmse.common.pkgs.PkgRepo,
    private val areaManager: DataAreaManager,
    private val storageManager: StorageManager,
    private val gatewaySwitch: GatewaySwitch,
) : LocalCSIProcessor {

    override suspend fun hasJurisdiction(type: DataArea.Type): Boolean = type == DataArea.Type.PUBLIC_OBB

    override suspend fun identifyArea(target: APath): AreaInfo? {
        var primary: DataArea? = null

        for (area in areaManager.currentAreas().filter { it.type == DataArea.Type.PUBLIC_OBB }) {
            if (area.hasFlags(DataArea.Flag.PRIMARY)) primary = area

            if (area.path.isAncestorOf(target)) {
                return AreaInfo(
                    dataArea = area,
                    file = target,
                    prefix = area.path,
                    isBlackListLocation = true
                )
            }
        }

        if (target.containsSegments(*BASE_SEGMENTS) && primary != null && LEGACY_PATH.isAncestorOf(target)) {
            return AreaInfo(
                dataArea = primary,
                file = target,
                prefix = LEGACY_PATH.child(*BASE_SEGMENTS),
                isBlackListLocation = true,
            )
        }

        return null
    }

    override suspend fun findOwners(areaInfo: AreaInfo): CSIProcessor.Result {
        require(hasJurisdiction(areaInfo.type)) { "Wrong jurisdiction: ${areaInfo.type}" }

        val owners = mutableSetOf<Owner>()
        var hasKnownUnknownOwner = false

        val dirNameAsPkg = areaInfo.prefixFreePath.first()

        if (pkgRepo.isInstalled(dirNameAsPkg.toPkgId())) {
            owners.add(Owner(dirNameAsPkg.toPkgId()))
        } else {
            clutterRepo.match(areaInfo.type, listOf(dirNameAsPkg))
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
        @Binds @IntoSet abstract fun mod(mod: PublicObbCSI): CSIProcessor
    }

    companion object {
        val TAG: String = logTag("CSI", "Public", "Obb")
        val BASE_SEGMENTS = arrayOf("Android", "obb")
    }
}