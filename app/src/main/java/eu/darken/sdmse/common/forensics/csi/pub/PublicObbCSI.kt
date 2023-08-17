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
import eu.darken.sdmse.common.clutter.ClutterRepo
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.*
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.CSIProcessor
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.forensics.csi.LocalCSIProcessor
import eu.darken.sdmse.common.forensics.csi.toOwners
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.isInstalled
import eu.darken.sdmse.common.pkgs.toPkgId
import javax.inject.Inject

@Reusable
class PublicObbCSI @Inject constructor(
    private val clutterRepo: ClutterRepo,
    private val pkgRepo: PkgRepo,
    private val areaManager: DataAreaManager,
    private val storageManager: StorageManager,
    private val gatewaySwitch: GatewaySwitch,
) : LocalCSIProcessor {

    override suspend fun hasJurisdiction(type: DataArea.Type): Boolean = type == DataArea.Type.PUBLIC_OBB

    @Suppress("SimplifiableCallChain")
    override suspend fun identifyArea(target: APath): AreaInfo? = areaManager.currentAreas()
        .filter { it.type == DataArea.Type.PUBLIC_OBB }
        .filter { it.path.isAncestorOf(target) }
        .singleOrNull()
        ?.let {
            AreaInfo(
                dataArea = it,
                file = target,
                prefix = it.path,
                isBlackListLocation = true
            )
        }

    override suspend fun findOwners(areaInfo: AreaInfo): CSIProcessor.Result {
        require(hasJurisdiction(areaInfo.type)) { "Wrong jurisdiction: ${areaInfo.type}" }

        val owners = mutableSetOf<Owner>()
        var hasKnownUnknownOwner = false

        val userHandle = areaInfo.userHandle
        val dirNameAsPkg = areaInfo.prefixFreeSegments.first()

        if (pkgRepo.isInstalled(dirNameAsPkg.toPkgId(), userHandle)) {
            owners.add(Owner(dirNameAsPkg.toPkgId(), userHandle))
        } else {
            clutterRepo.match(areaInfo.type, listOf(dirNameAsPkg))
                .map { it.toOwners(areaInfo) }
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