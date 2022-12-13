package eu.darken.sdmse.corpsefinder.core.filter

import android.content.Context
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
import eu.darken.sdmse.common.castring.toCaString
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.files.core.GatewaySwitch
import eu.darken.sdmse.common.files.core.listFiles
import eu.darken.sdmse.common.files.core.local.LocalGateway
import eu.darken.sdmse.common.files.core.walk
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.progress.*
import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.corpsefinder.core.CorpseFinderSettings
import eu.darken.sdmse.corpsefinder.core.RiskLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.toSet
import java.io.IOException
import javax.inject.Inject


@Reusable
class PrivateDataCorpseFilter @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val areaManager: DataAreaManager,
    private val gatewaySwitch: GatewaySwitch,
    private val fileForensics: FileForensics,
    private val corpseFinderSettings: CorpseFinderSettings,
) : CorpseFilter(
    TAG,
    appScope = appScope
) {

    override suspend fun scan(): Collection<Corpse> {
        if (corpseFinderSettings.filterPrivateDataEnabled.value()) {
            log(TAG, VERBOSE) { "Scanning..." }
        } else {
            log(TAG) { "Filter is disabled" }
            return emptyList()
        }

        gatewaySwitch.addParent(this)

        val gateway = gatewaySwitch.getGateway(APath.PathType.LOCAL) as LocalGateway

        if (!gateway.hasRoot()) {
            log(TAG, INFO) { "LocalGateway has no root, skipping private data" }
            return emptySet()
        }

        return areaManager.currentAreas()
            .filter { it.type == DataArea.Type.PRIVATE_DATA }
            .map { area ->
                updateProgressPrimary(
                    { c: Context -> c.getString(R.string.general_progress_processing_x, area.label) }.toCaString()
                )
                log(TAG) { "Reading $area" }
                updateProgressSecondary(R.string.general_progress_searching)
                val topLevelContents = area.path.listFiles(gatewaySwitch)

                log(TAG) { "Filtering $area" }
                updateProgressSecondary(R.string.general_progress_filtering)
                doFilter(topLevelContents)
            }
            .flatten()
    }

    @Throws(IOException::class) private suspend fun doFilter(candidates: List<APath>): Collection<Corpse> {
        updateProgressCount(Progress.Count.Counter(0, candidates.size))

        val includeRiskUserGenerated: Boolean = corpseFinderSettings.includeRiskUserGenerated.value()
        val includeRiskCommon: Boolean = corpseFinderSettings.includeRiskCommon.value()

        return candidates
            .onEach { increaseProgress() }
            .filter { !shouldBeExcluded(it) }
            .map { fileForensics.findOwners(it) }
            .filter { ownerInfo ->
                (ownerInfo.areaInfo.type == DataArea.Type.PRIVATE_DATA).also {
                    if (!it) log(TAG, WARN) { "Wrong area: $ownerInfo" }
                }
            }
            .filter { !it.isCurrentlyOwned }
            .filter { !it.isKeeper || includeRiskUserGenerated }
            .filter { !it.isCommon || includeRiskCommon }
            .filter { it.isCorpse }
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
            .toList()
    }

    private fun shouldBeExcluded(inspectedDir: APath): Boolean = when (inspectedDir.name) {
        "hosts" -> true
        "lost+found" -> true
        else -> false
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: PrivateDataCorpseFilter): CorpseFilter
    }

    companion object {
        val TAG: String = logTag("CorpseFinder", "Filter", "PrivateData")
    }
}