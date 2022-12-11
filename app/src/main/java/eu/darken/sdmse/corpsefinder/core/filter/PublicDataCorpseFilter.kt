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
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.files.core.GatewaySwitch
import eu.darken.sdmse.common.files.core.listFiles
import eu.darken.sdmse.common.files.core.walk
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.forensics.RiskLevel
import eu.darken.sdmse.common.progress.*
import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.corpsefinder.core.CorpseFinderSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.toSet
import java.io.IOException
import javax.inject.Inject

@Reusable
class PublicDataCorpseFilter @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val areaManager: DataAreaManager,
    private val gatewaySwitch: GatewaySwitch,
    private val fileForensics: FileForensics,
    private val corpseFinderSettings: CorpseFinderSettings,
) : CorpseFilter(
    TAG,
    appScope = appScope
) {

    override suspend fun filter(): Collection<Corpse> = gatewaySwitch.useSharedResource {
        areaManager.currentAreas()
            .filter { it.type == DataArea.Type.PUBLIC_DATA }
            .map { area ->
                updateProgressPrimary(area.label)
                log(TAG) { "Reading $area" }
                updateProgressSecondary(R.string.general_progress_searching)
                val pubDataContents = area.path.listFiles(gatewaySwitch)

                log(TAG) { "Filtering $area" }
                updateProgressSecondary(R.string.general_progress_filtering)
                doFilter(pubDataContents)
            }
            .flatten()
    }

    @Throws(IOException::class) private suspend fun doFilter(candidates: List<APath>): Collection<Corpse> {
        updateProgressCount(Progress.Count.Counter(0, candidates.size))

        val removeKeepers: Boolean = true // corpseFinderSettings

        return candidates
            .onEach { increaseProgress() }
            .filter { !shouldBeExcluded(it) }
            .map { fileForensics.findOwners(it) }
            .filter { ownerInfo ->
                (ownerInfo.areaInfo.type == DataArea.Type.PUBLIC_DATA).also {
                    if (!it) log(TAG, WARN) { "Wrong area: $ownerInfo" }
                }
            }
            .filter { !it.isCurrentlyOwned() }
            .filter { !it.isKeeper || removeKeepers }
            .filter { !it.isCommon }
            .filter { it.isCorpse }
            .map { ownerInfo ->
                val content = ownerInfo.item.walk(gatewaySwitch).toSet()
                Corpse(
                    path = ownerInfo.item,
                    areaInfo = ownerInfo.areaInfo,
                    content = content,
                    isWriteProtected = false,
                    riskLevel = RiskLevel.NORMAL
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


    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: PublicDataCorpseFilter): CorpseFilter
    }

    companion object {
        val TAG: String = logTag("CorpseFinder", "Filter", "PublicData")
    }
}