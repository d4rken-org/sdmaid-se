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
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.CSIProcessor
import eu.darken.sdmse.common.forensics.csi.LocalCSIProcessor
import eu.darken.sdmse.common.forensics.csi.dalvik.tools.*
import javax.inject.Inject

@Reusable
class DalvikDexCSI @Inject constructor(
    private val areaManager: DataAreaManager,
    private val sourceGenerator: DalvikCandidateGenerator,
    private val clutterCheck: DalvikClutterCheck,
    private val customDexOptCheck: CustomDexOptCheck,
    private val sourceDirCheck: SourceDirCheck,
    private val apkCheck: ApkCheck,
    private val existCheck: ExistCheck,
    private val oddOnesCheck: OddOnesCheck,
) : LocalCSIProcessor {

    override suspend fun hasJurisdiction(type: DataArea.Type): Boolean = type == DataArea.Type.DALVIK_DEX

    override suspend fun identifyArea(target: APath): AreaInfo? = areaManager.currentAreas()
        .filter { it.type == DataArea.Type.DALVIK_DEX }
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
        val basePath = areaInfo.file as? LocalPath ?: return CSIProcessor.Result()

        val candidates = sourceGenerator.getCandidates(basePath).toMutableList()

        sourceDirCheck.check(areaInfo, candidates)
            .takeIf { !it.isEmpty() }
            ?.let { return CSIProcessor.Result(it.owners, it.hasKnownUnknownOwner) }

        customDexOptCheck.check(areaInfo).let { (result, extraPath) ->
            extraPath?.let { candidates.add(it) }
            if (!result.isEmpty()) {
                return CSIProcessor.Result(result.owners, result.hasKnownUnknownOwner)
            }
        }

        clutterCheck.process(areaInfo)
            .takeIf { !it.isEmpty() }
            ?.let { return CSIProcessor.Result(it.owners, it.hasKnownUnknownOwner) }

        apkCheck.check(areaInfo, candidates)
            .takeIf { !it.isEmpty() }
            ?.let { return CSIProcessor.Result(it.owners, it.hasKnownUnknownOwner) }

        existCheck.check(candidates)
            .takeIf { !it.isEmpty() }
            ?.let { return CSIProcessor.Result(it.owners, it.hasKnownUnknownOwner) }

        oddOnesCheck.check(areaInfo)
            .takeIf { !it.isEmpty() }
            ?.let { return CSIProcessor.Result(it.owners, it.hasKnownUnknownOwner) }

        return CSIProcessor.Result()
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: DalvikDexCSI): CSIProcessor
    }

    companion object {
        val TAG: String = logTag("CSI", "Dalvik", "Dex")
    }
}