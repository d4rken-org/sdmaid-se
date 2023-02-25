package eu.darken.sdmse.exclusion.core

import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.exclusion.core.types.PackageExclusion
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExclusionStorage @Inject constructor() {

    suspend fun save(exclusions: Set<Exclusion>) {
        log(TAG) { "save(): ${exclusions.size}" }
    }

    suspend fun load(): Set<Exclusion> {
        val exclusions = setOf<Exclusion>(
            PathExclusion(
                path = LocalPath.build("/storage/emulated/0")
            ),
            PackageExclusion(
                pkgId = "some.pkg".toPkgId()
            ),
            PackageExclusion(
                pkgId = "eu.darken.capod".toPkgId()
            )
        )
        log(TAG) { "load(): ${exclusions.size}" }
        return exclusions
    }

    companion object {
        private val TAG = logTag("Exclusion", "Storage")
    }
}