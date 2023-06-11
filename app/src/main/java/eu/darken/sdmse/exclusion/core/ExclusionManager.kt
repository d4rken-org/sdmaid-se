package eu.darken.sdmse.exclusion.core

import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.DynamicStateFlow
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.ExclusionId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExclusionManager @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    private val exclusionStorage: ExclusionStorage,
) {

    private val _exclusions = DynamicStateFlow(parentScope = appScope + dispatcherProvider.IO) {
        exclusionStorage.load() ?: emptySet()
    }
    val exclusions: Flow<Collection<Exclusion>> = _exclusions.flow

    init {
        _exclusions.flow
            .onEach { exclusionStorage.save(it) }
            .launchIn(appScope + dispatcherProvider.IO)
    }

    suspend fun save(exclusions: Set<Exclusion>) {
        log(TAG) { "save(): $exclusions" }
        _exclusions.updateBlocking {
            val toOverwrite = exclusions.map { it.id }
            this
                .filter { !toOverwrite.contains(it.id) }
                .plus(exclusions).toSet()
        }
    }

    suspend fun remove(ids: Set<ExclusionId>) {
        log(TAG) { "remove(): $ids" }
        val targets = currentExclusions().filter { ids.contains(it.id) }.toSet()
        log(TAG) { "remove(): $targets" }
        _exclusions.updateBlocking { this - targets }
    }

    companion object {
        private val TAG = logTag("Exclusion", "Manager")
    }
}