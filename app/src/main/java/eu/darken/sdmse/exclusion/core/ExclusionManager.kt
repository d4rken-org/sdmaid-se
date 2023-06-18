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

    suspend fun save(toSave: Set<Exclusion>): Collection<Exclusion> {
        log(TAG) { "save(): $toSave" }
        val newOrUpdated = mutableSetOf<Exclusion>()
        _exclusions.updateBlocking {
            val newExclusions = toSave.filter {
                val isDupe = this.contains(it)
                if (isDupe) log(TAG) { "Exclusion already exists: $it" }
                else newOrUpdated.add(it)
                !isDupe
            }
            this
                .filter { old -> newExclusions.none { old.id == it.id } }
                .plus(newExclusions).toSet()
        }
        return newOrUpdated
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