package eu.darken.sdmse.corpsefinder.core.filter

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.R
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.areas.currentAreas
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.*
import eu.darken.sdmse.common.files.local.LocalGateway
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.progress.*
import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.corpsefinder.core.CorpseFinderSettings
import eu.darken.sdmse.corpsefinder.core.RiskLevel
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.pathExclusions
import eu.darken.sdmse.main.core.SDMTool
import kotlinx.coroutines.flow.*
import java.io.IOException
import javax.inject.Inject
import javax.inject.Provider

@Reusable
class PublicDataCorpseFilter @Inject constructor(
    private val areaManager: DataAreaManager,
    private val gatewaySwitch: GatewaySwitch,
    private val fileForensics: FileForensics,
    private val corpseFinderSettings: CorpseFinderSettings,
    private val exclusionManager: ExclusionManager,
) : CorpseFilter(TAG, Progress.Data(primary = R.string.corpsefinder_filter_publicdata_label.toCaString())) {

    override suspend fun doScan(): Collection<Corpse> {
        log(TAG) { "Scanning..." }

        val gateway = gatewaySwitch.getGateway(APath.PathType.LOCAL) as LocalGateway

        if (hasApiLevel(33) && !gateway.hasRoot() && !gateway.hasShizuku()) {
            log(TAG) { "LocalGateway has no root/adb, skipping public data on Android 13+" }
            return emptySet()
        }

        updateProgressPrimary(R.string.corpsefinder_filter_publicdata_label)

        val pathExclusions = exclusionManager.pathExclusions(SDMTool.Type.CORPSEFINDER)

        return areaManager.currentAreas()
            .filter { it.type == DataArea.Type.PUBLIC_DATA }
            .map { area ->
                updateProgressSecondary {
                    it.getString(eu.darken.sdmse.common.R.string.general_progress_processing_x, area.label.get(it))
                }

                log(TAG) { "Reading $area" }
                val topLevelContents = area.path
                    .listFiles(gatewaySwitch)
                    .filter { path ->
                        pathExclusions.none { excl ->
                            excl.match(path).also {
                                if (it) log(TAG, INFO) { "Excluded due to $excl: $path" }
                            }
                        }
                    }

                log(TAG) { "Filtering $area" }
                doFilter(topLevelContents)
            }
            .flatten()
    }

    @Throws(IOException::class) private suspend fun doFilter(candidates: Collection<APath>): Collection<Corpse> {
        updateProgressCount(Progress.Count.Percent(candidates.size))

        val includeRiskKeeper: Boolean = corpseFinderSettings.includeRiskKeeper.value()
        val includeRiskCommon: Boolean = corpseFinderSettings.includeRiskCommon.value()

        return candidates
            .asFlow()
            .filter { !shouldBeExcluded(it) }
            .mapNotNull {
                log(TAG) { "Checking $it" }
                increaseProgress()
                fileForensics.findOwners(it)
            }
            .filter { ownerInfo ->
                (ownerInfo.areaInfo.type == DataArea.Type.PUBLIC_DATA).also {
                    if (!it) log(TAG, WARN) { "Wrong area: $ownerInfo" }
                }
            }
            .filter { it.isCorpse }
            .filter { !it.isKeeper || includeRiskKeeper }
            .filter { !it.isCommon || includeRiskCommon }
            .map { ownerInfo ->
                val lookup = ownerInfo.item.lookup(gatewaySwitch)
                val content = if (lookup.isDirectory) ownerInfo.item.walk(gatewaySwitch).toSet() else emptyList()
                Corpse(
                    filterType = this::class,
                    ownerInfo = ownerInfo,
                    lookup = lookup,
                    content = content,
                    isWriteProtected = false,
                    riskLevel = when {
                        ownerInfo.isKeeper -> RiskLevel.KEEPER
                        ownerInfo.isCommon -> RiskLevel.COMMON
                        else -> RiskLevel.NORMAL
                    }
                ).also { log(TAG, INFO) { "Found Corpse: $it" } }
            }
            .toList()
    }

    private fun shouldBeExcluded(inspectedDir: APath): Boolean = when (inspectedDir.name) {
        ".nomedia" -> true
        "hosts" -> true
        "lost+found" -> true
        else -> false
    }

    @Reusable
    class Factory @Inject constructor(
        private val settings: CorpseFinderSettings,
        private val filterProvider: Provider<PublicDataCorpseFilter>
    ) : CorpseFilter.Factory {
        override suspend fun isEnabled(): Boolean = settings.filterPublicDataEnabled.value()
        override suspend fun create(): CorpseFilter = filterProvider.get()
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Factory): CorpseFilter.Factory
    }

    companion object {
        val TAG: String = logTag("CorpseFinder", "Filter", "PublicData")
    }
}