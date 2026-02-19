package eu.darken.sdmse.common.forensics.csi.dalvik

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.areas.currentAreas
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.isAncestorOf
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.CSIProcessor
import eu.darken.sdmse.common.forensics.csi.LocalCSIProcessor
import eu.darken.sdmse.common.forensics.csi.dalvik.tools.DalvikClutterCheck
import eu.darken.sdmse.common.forensics.csi.dalvik.tools.DirNameCheck
import javax.inject.Inject

@Reusable
class DalvikProfileCSI @Inject constructor(
    private val areaManager: DataAreaManager,
    private val dirNameCheck: DirNameCheck,
    private val clutterCheck: DalvikClutterCheck,
) : LocalCSIProcessor {

    override suspend fun hasJurisdiction(type: DataArea.Type): Boolean = type == DataArea.Type.DALVIK_PROFILE

    override suspend fun identifyArea(target: APath): AreaInfo? = areaManager.currentAreas()
        .filter { it.type == DataArea.Type.DALVIK_PROFILE }
        .mapNotNull { area ->
            if (!area.path.isAncestorOf(target)) return@mapNotNull null

            AreaInfo(
                dataArea = area,
                file = target,
                prefix = area.path,
                isBlackListLocation = true
            )
        }
        .singleOrNull()

    override suspend fun findOwners(areaInfo: AreaInfo): CSIProcessor.Result {
        val results = mutableSetOf<DalvikCheck.Result>()
        results.add(dirNameCheck.process(areaInfo))

        if (results.all { it.owners.isEmpty() }) {
            results.add(clutterCheck.process(areaInfo))
        }

        return CSIProcessor.Result(
            owners = results.map { it.owners }.flatten().toSet(),
            hasKnownUnknownOwner = results.any { it.hasKnownUnknownOwner },
        )
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: DalvikProfileCSI): CSIProcessor
    }

    companion object {
        val TAG: String = logTag("CSI", "Dalvik", "Profile")
    }
}