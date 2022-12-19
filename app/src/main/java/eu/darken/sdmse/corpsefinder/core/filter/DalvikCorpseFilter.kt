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
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.files.core.GatewaySwitch
import eu.darken.sdmse.common.files.core.listFiles
import eu.darken.sdmse.common.files.core.local.LocalGateway
import eu.darken.sdmse.common.files.core.walk
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.pkgs.getSharedLibraries2
import eu.darken.sdmse.common.progress.*
import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.corpsefinder.core.CorpseFinderSettings
import eu.darken.sdmse.corpsefinder.core.RiskLevel
import kotlinx.coroutines.flow.toSet
import java.util.*
import javax.inject.Inject

@Reusable
class DalvikCorpseFilter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val areaManager: DataAreaManager,
    private val gatewaySwitch: GatewaySwitch,
    private val fileForensics: FileForensics,
    private val corpseFinderSettings: CorpseFinderSettings,
) : CorpseFilter(TAG) {

    override suspend fun scan(): Collection<Corpse> {
        if (!corpseFinderSettings.filterDalvikCacheEnabled.value()) {
            log(TAG) { "Filter is disabled" }
            return emptyList()
        }
        log(TAG) { "Scanning..." }

        val gateway = gatewaySwitch.getGateway(APath.PathType.LOCAL) as LocalGateway

        if (!gateway.hasRoot()) {
            log(TAG, INFO) { "LocalGateway has no root, skipping." }
            return emptySet()
        }

        val includeRiskKeeper: Boolean = corpseFinderSettings.includeRiskKeeper.value()
        val includeRiskCommon: Boolean = corpseFinderSettings.includeRiskCommon.value()

        val areas = areaManager.currentAreas()

        val profileCorpses = areas
            .filter { it.type == DataArea.Type.DALVIK_PROFILE }
            .map { area ->
                updateProgressPrimary(
                    { c: Context -> c.getString(R.string.general_progress_processing_x, area.label) }.toCaString()
                )
                log(TAG) { "Reading $area" }
                updateProgressSecondary(R.string.general_progress_searching)
                updateProgressCount(Progress.Count.Indeterminate())

                area.path.listFiles(gatewaySwitch)
            }
            .map { profilesToCheck ->
                updateProgressSecondary(R.string.general_progress_filtering)
                doFilterDalvikProfiles(profilesToCheck, includeRiskKeeper, includeRiskCommon)
            }
            .flatten()

        val dalvikCorpses = areas
            .filter { it.type == DataArea.Type.DALVIK_DEX }
            .map { area ->
                updateProgressPrimary(
                    { c: Context -> c.getString(R.string.general_progress_processing_x, area.label) }.toCaString()
                )
                log(TAG) { "Reading $area" }
                updateProgressSecondary(R.string.general_progress_searching)
                updateProgressCount(Progress.Count.Indeterminate())

                area.path.listFiles(gatewaySwitch)
            }
            .map { dalvikFilesToCheck ->
                updateProgressSecondary(R.string.general_progress_filtering)
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
        profileItems: List<APath>,
        includeRiskKeeper: Boolean,
        includeRiskCommon: Boolean,
    ): Collection<Corpse> {
        log(TAG) { "doFilterDalvikProfiles(${profileItems.size}, keeper=$includeRiskKeeper, common=$includeRiskCommon)" }
        updateProgressCount(Progress.Count.Counter(max = profileItems.size))

        return profileItems
            .onEach { log(TAG) { "Checking profile $it" } }
            .map { fileForensics.findOwners(it) }
            .filter { ownerInfo ->
                (ownerInfo.areaInfo.type == DataArea.Type.DALVIK_PROFILE).also {
                    if (!it) log(TAG, WARN) { "Wrong area: $ownerInfo" }
                }
            }
            .filter { it.isCorpse }
            .filter { !it.isKeeper || includeRiskKeeper }
            .filter { !it.isCommon || includeRiskCommon }
            .map { ownerInfo ->
                val content = ownerInfo.item.walk(gatewaySwitch).toSet()
                Corpse(
                    ownerInfo = ownerInfo,
                    content = content,
                    isWriteProtected = false,
                    riskLevel = when {
                        ownerInfo.isKeeper -> RiskLevel.USER_GENERATED
                        ownerInfo.isCommon -> RiskLevel.COMMON
                        else -> RiskLevel.NORMAL
                    }
                ).also { log(TAG, INFO) { "Found Corpse: $it" } }
            }
            .onEach { increaseProgress() }
    }

    private suspend fun doFilterOdex(
        dalvikItems: List<APath>,
        includeRiskKeeper: Boolean,
        includeRiskCommon: Boolean,
    ): Collection<Corpse> {
        log(TAG) { "doFilterOdex(${dalvikItems.size}, keeper=$includeRiskKeeper, common=$includeRiskCommon)" }
        updateProgressCount(Progress.Count.Counter(max = dalvikItems.size))

        return dalvikItems
            .onEach { log(TAG) { "Checking dex $it" } }
            .map { fileForensics.findOwners(it) }
            .filter { ownerInfo ->
                (ownerInfo.areaInfo.type == DataArea.Type.DALVIK_DEX).also {
                    if (!it) log(TAG, WARN) { "Wrong area: $ownerInfo" }
                }
            }
            .filter { it.isCorpse }
            .filter { !it.isKeeper || includeRiskKeeper }
            .filter { !it.isCommon || includeRiskCommon }
            .map { ownerInfo ->
                val content = ownerInfo.item.walk(gatewaySwitch).toSet()
                Corpse(
                    ownerInfo = ownerInfo,
                    content = content,
                    isWriteProtected = false,
                    riskLevel = when {
                        ownerInfo.isKeeper -> RiskLevel.USER_GENERATED
                        ownerInfo.isCommon -> RiskLevel.COMMON
                        else -> RiskLevel.NORMAL
                    }
                ).also { log(TAG, INFO) { "Found Corpse: $it" } }
            }
            .onEach { increaseProgress() }
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: DalvikCorpseFilter): CorpseFilter
    }

    companion object {
        val TAG: String = logTag("CorpseFinder", "Filter", "Dalvik")
    }
}
