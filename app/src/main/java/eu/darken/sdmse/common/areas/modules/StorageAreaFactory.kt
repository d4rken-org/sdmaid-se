package eu.darken.sdmse.common.areas.modules

import dagger.Reusable
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.BuildConfigWrap.BuildType.RELEASE
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.GatewaySwitch
import javax.inject.Inject

@Reusable
class StorageAreaFactory @Inject constructor(
    private val gatewaySwitch: GatewaySwitch,
    private val areaModules: Set<@JvmSuppressWildcards DataAreaModule>,
) {

    suspend fun build(): Collection<DataArea> = gatewaySwitch.use {
        val firstPassResult = areaModules.map { it.firstPass() }.flatten()
        log(TAG, VERBOSE) { "build(): First pass: ${firstPassResult.joinToString("\n")}" }

        val secondPass = areaModules.map { it.secondPass(firstPassResult) }.flatten()
        log(TAG, VERBOSE) { "build(): Second pass:\n${secondPass.joinToString("\n")}" }

        val uniqueAreas = secondPass.toSet()
        if (secondPass.size != uniqueAreas.size) {
            log(TAG, WARN) { "build(): Cleaned areas: ${uniqueAreas.joinToString("\n")}" }
        }

        if (BuildConfigWrap.BUILD_TYPE != RELEASE && uniqueAreas.size != secondPass.size) {
            throw IllegalStateException("Duplicate data areas")
        }

        if (BuildConfigWrap.BUILD_TYPE != RELEASE && uniqueAreas.map { it.path }.toSet().size != uniqueAreas.size) {
            throw IllegalStateException("Duplicate data areas with overlapping paths")
        }

        log(TAG, INFO) { "Detected storage areas:\n${uniqueAreas.joinToString("\n")}" }

        secondPass
    }

    companion object {
        private val TAG = logTag("DataArea", "Factory")
    }
}