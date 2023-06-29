package eu.darken.sdmse.corpsefinder.core.filter

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.R
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.areas.currentAreas
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.*
import eu.darken.sdmse.common.files.local.LocalGateway
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.pkgs.getSharedLibraries2
import eu.darken.sdmse.common.progress.*
import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.corpsefinder.core.CorpseFinderSettings
import eu.darken.sdmse.corpsefinder.core.RiskLevel
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.pathExclusions
import eu.darken.sdmse.main.core.SDMTool
import kotlinx.coroutines.flow.*
import java.util.*
import javax.inject.Inject
import javax.inject.Provider

@Reusable
class DalvikCorpseFilter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val areaManager: DataAreaManager,
    private val gatewaySwitch: GatewaySwitch,
    private val fileForensics: FileForensics,
    private val corpseFinderSettings: CorpseFinderSettings,
    private val exclusionManager: ExclusionManager,
) : CorpseFilter(TAG, DEFAULT_PROGRESS) {

    override suspend fun doScan(): Collection<Corpse> {
        log(TAG) { "Scanning..." }

        val gateway = gatewaySwitch.getGateway(APath.PathType.LOCAL) as LocalGateway

        if (!gateway.hasRoot()) {
            log(TAG) { "LocalGateway has no root, skipping." }
            return emptySet()
        }

        updateProgressPrimary(R.string.corpsefinder_filter_dalvik_label)

        val includeRiskKeeper: Boolean = corpseFinderSettings.includeRiskKeeper.value()
        val includeRiskCommon: Boolean = corpseFinderSettings.includeRiskCommon.value()

        val areas = areaManager.currentAreas()

        val pathExclusions = exclusionManager.pathExclusions(SDMTool.Type.CORPSEFINDER)

        val profileCorpses = areas
            .filter { it.type == DataArea.Type.DALVIK_PROFILE }
            .map { area ->
                updateProgressSecondary {
                    it.getString(eu.darken.sdmse.common.R.string.general_progress_processing_x, area.label.get(it))
                }
                updateProgressCount(Progress.Count.Indeterminate())

                log(TAG) { "Reading $area" }
                area.path
                    .listFiles(gatewaySwitch)
                    .filter { path ->
                        pathExclusions.none { excl ->
                            excl.match(path).also {
                                if (it) log(TAG, INFO) { "Excluded due to $excl: $path" }
                            }
                        }
                    }
            }
            .map { profilesToCheck ->
                updateProgressSecondary(eu.darken.sdmse.common.R.string.general_progress_filtering)
                doFilterDalvikProfiles(profilesToCheck, includeRiskKeeper, includeRiskCommon)
            }
            .flatten()

        val dalvikCorpses = areas
            .filter { it.type == DataArea.Type.DALVIK_DEX }
            .map { area ->
                updateProgressSecondary {
                    it.getString(eu.darken.sdmse.common.R.string.general_progress_processing_x, area.label.get(it))
                }
                updateProgressCount(Progress.Count.Indeterminate())

                log(TAG) { "Reading $area" }
                area.path
                    .listFiles(gatewaySwitch)
                    .filter { path ->
                        pathExclusions.none { excl ->
                            excl.match(path).also {
                                if (it) log(TAG, INFO) { "Excluded due to $excl: $path" }
                            }
                        }
                    }
            }
            .map { dalvikFilesToCheck ->
                updateProgressSecondary(eu.darken.sdmse.common.R.string.general_progress_filtering)
                doFilterOdex(dalvikFilesToCheck, includeRiskKeeper, includeRiskCommon)
            }
            .flatten()

        return checkForLibs(profileCorpses + dalvikCorpses)
    }

    private fun checkForLibs(corpses: Collection<Corpse>): Collection<Corpse> {
        val libNames = context.packageManager.getSharedLibraries2(0).map { it.name }

        return corpses
            .filter { corpse ->
                val match = corpse.ownerInfo.owners.find { libNames.contains(it.pkgId.name) }
                if (match != null) log(TAG) { "Ignoring corpse due to library match: $corpse with $match" }
                match == null
            }
    }

    private suspend fun doFilterDalvikProfiles(
        profileItems: Collection<APath>,
        includeRiskKeeper: Boolean,
        includeRiskCommon: Boolean,
    ): Collection<Corpse> {
        log(TAG) { "doFilterDalvikProfiles(${profileItems.size}, keeper=$includeRiskKeeper, common=$includeRiskCommon)" }
        updateProgressCount(Progress.Count.Percent(profileItems.size))

        return profileItems
            .asFlow()
            .mapNotNull {
                log(TAG) { "Checking $it" }
                increaseProgress()
                fileForensics.findOwners(it)
            }
            .filter { ownerInfo ->
                (ownerInfo.areaInfo.type == DataArea.Type.DALVIK_PROFILE).also {
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

    private suspend fun doFilterOdex(
        dalvikItems: Collection<APath>,
        includeRiskKeeper: Boolean,
        includeRiskCommon: Boolean,
    ): Collection<Corpse> {
        log(TAG) { "doFilterOdex(${dalvikItems.size}, keeper=$includeRiskKeeper, common=$includeRiskCommon)" }
        updateProgressCount(Progress.Count.Percent(dalvikItems.size))

        return dalvikItems
            .asFlow()
            .mapNotNull {
                log(TAG) { "Checking $it" }
                increaseProgress()
                fileForensics.findOwners(it)
            }
            .filter { ownerInfo ->
                (ownerInfo.areaInfo.type == DataArea.Type.DALVIK_DEX).also {
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

    @Reusable
    class Factory @Inject constructor(
        private val settings: CorpseFinderSettings,
        private val filterProvider: Provider<DalvikCorpseFilter>
    ) : CorpseFilter.Factory {
        override suspend fun isEnabled(): Boolean = settings.filterDalvikCacheEnabled.value()
        override suspend fun create(): CorpseFilter = filterProvider.get()
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Factory): CorpseFilter.Factory
    }

    companion object {
        val DEFAULT_PROGRESS = Progress.Data(
            primary = R.string.corpsefinder_filter_dalvik_label.toCaString(),
            secondary = eu.darken.sdmse.common.R.string.general_progress_loading.toCaString(),
            count = Progress.Count.Indeterminate()
        )
        val TAG: String = logTag("CorpseFinder", "Filter", "Dalvik")
    }
}
