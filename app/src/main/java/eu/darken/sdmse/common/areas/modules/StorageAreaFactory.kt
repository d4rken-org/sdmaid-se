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

    suspend fun build(): Collection<DataArea> = gatewaySwitch.useSharedResource {
        val firstPass = areaModules.map { it.firstPass() }.flatten()
        log(TAG, VERBOSE) { "build(): First pass: ${firstPass.joinToString("\n")}" }

        val secondPass = areaModules.map { it.secondPass(firstPass) }.flatten()
        log(TAG, VERBOSE) { "build(): Second pass:\n${secondPass.joinToString("\n")}" }

        val newAreas = (firstPass + secondPass).toSet()
        if (firstPass.size + secondPass.size != newAreas.size) {
            log(TAG, WARN) { "build(): Cleaned areas: ${newAreas.joinToString("\n")}" }
        }

        if (BuildConfigWrap.BUILD_TYPE != RELEASE && firstPass.size + secondPass.size != newAreas.size) {
            throw IllegalStateException("Duplicate data areas")
        }

        if (BuildConfigWrap.BUILD_TYPE != RELEASE && newAreas.map { it.path }.toSet().size != newAreas.size) {
            throw IllegalStateException("Duplicate data areas with overlapping paths")
        }

        if (newAreas.isEmpty()) {
            log(TAG, ERROR) { "No accessible data areas detected!" }
        } else {
            log(TAG, INFO) { "Accessible data areas:\n${newAreas.joinToString("\n")}" }
        }

        secondPass
    }

    companion object {
        private val TAG = logTag("DataArea", "Factory")
    }
}