package eu.darken.sdmse.exclusions.core

import eu.darken.sdmse.common.debug.logging.logTag
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExclusionManager @Inject constructor() {
    val exclusions: Flow<Collection<Exclusion>> = TODO()

    companion object {
        private val TAG = logTag("Exclusion", "Manager")
    }
}