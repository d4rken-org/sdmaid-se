package eu.darken.sdmse.common.areas.modules

import dagger.Reusable
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.BuildConfigWrap.BuildType.RELEASE
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.sharedresource.closeAll
import javax.inject.Inject

@Reusable
class DataAreaFactory @Inject constructor(
    private val pkgOps: PkgOps,
    private val gatewaySwitch: GatewaySwitch,
    private val areaModules: Set<@JvmSuppressWildcards DataAreaModule>,
) {

    suspend fun build(): Collection<DataArea> {
        log(TAG) { "build()" }
        val leases = setOf(pkgOps, gatewaySwitch).map { it.sharedResource.get() }

        val firstPass = areaModules.map { it.firstPass() }.flatten()
        log(TAG, VERBOSE) { "build(): First pass: ${firstPass.joinToString("\n")}" }

        val secondPass = areaModules.map { it.secondPass(firstPass) }.flatten()
        log(TAG, VERBOSE) { "build(): Second pass:\n${secondPass.joinToString("\n")}" }

        leases.closeAll()

        val newAreas = (firstPass + secondPass).toSet()
        if (firstPass.size + secondPass.size != newAreas.size) {
            log(TAG, WARN) { "build(): Cleaned areas: ${newAreas.joinToString("\n")}" }
        }

        if (BuildConfigWrap.BUILD_TYPE != RELEASE && firstPass.size + secondPass.size != newAreas.size) {
            log(TAG, WARN) { "First pass: $firstPass" }
            log(TAG, WARN) { "Second pass: $secondPass" }
            log(TAG, WARN) { "Final areas: $newAreas" }
            throw IllegalStateException("Duplicate data areas")
        }

        if (BuildConfigWrap.BUILD_TYPE != RELEASE && newAreas.map { it.path }.toSet().size != newAreas.size) {
            log(TAG, WARN) { "Final areas: $newAreas" }
            throw IllegalStateException("Duplicate data areas with overlapping paths")
        }

        if (newAreas.isEmpty()) {
            log(TAG, ERROR) { "No accessible data areas detected!" }
        } else {
            log(TAG, INFO) { "Accessible data areas:\n${newAreas.joinToString("\n")}" }
        }

        return newAreas
    }

    companion object {
        private val TAG = logTag("DataArea", "Factory")
    }
}