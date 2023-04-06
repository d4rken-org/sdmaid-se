package eu.darken.sdmse.common.forensics.csi.source

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
import eu.darken.sdmse.common.forensics.csi.source.tools.SimilarityFilter
import javax.inject.Inject

@Reusable
class AppSourceMainCSI @Inject constructor(
    private val areaManager: DataAreaManager,
    private val sourceChecks: Set<@JvmSuppressWildcards AppSourceCheck>,
    private val similarityFilter: SimilarityFilter,
) : LocalCSIProcessor {

    override suspend fun hasJurisdiction(type: DataArea.Type): Boolean = type == DataArea.Type.APP_APP

    override suspend fun identifyArea(target: APath): AreaInfo? = areaManager.currentAreas()
        .filter { it.type == DataArea.Type.APP_APP }
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

    // <5.0 devices have /data/app/<pkg>.apk
    // 5.0 devices have /data/app/<pkg>/base.apk
    // 5.1 devices have a mix of both -_-
    // 8.0 devices have /data/app/<pkg>-random/base.apk
    // 11.0 (like 8.0) devices have /data/app/com.google.audio.hearing.visualization.accessibility.scribe-A8Z2KHvb6Tz290E6hedJTw==/base.apk
    // 11.0 devices have /data/app/~~XQHB35lfqGmyB9HMHDbu-w==/com.android.chrome-ZoTGgkXBDD_n7iNl4-aM7A==/base.apk
    override suspend fun findOwners(areaInfo: AreaInfo): CSIProcessor.Result {
        require(hasJurisdiction(areaInfo.type)) { "Wrong jurisdiction: ${areaInfo.type}" }

        return sourceChecks
            .map { it.process(areaInfo) }
            .let { subResults ->
                CSIProcessor.Result(
                    owners = subResults
                        .map { it.owners }
                        .flatten()
                        .let { similarityFilter.filterFalsePositives(areaInfo, it) }
                        .toSet(),
                    hasKnownUnknownOwner = subResults.any { it.hasKnownUnknownOwner },
                )
            }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: AppSourceMainCSI): CSIProcessor
    }

    companion object {
        val TAG: String = logTag("CSI", "AppSource", "Main")
    }
}