package eu.darken.sdmse.exclusion.core

import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.DynamicStateFlow
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.ExclusionId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.plus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExclusionManager @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    private val exclusionStorage: ExclusionStorage,
    private val defaultExclusions: DefaultExclusions,
) {

    // TODO Think about making this a SharedResource?
    private val _exclusions = DynamicStateFlow(parentScope = appScope + dispatcherProvider.IO) {
        exclusionStorage.load() ?: emptySet()
    }
    val exclusions: Flow<Collection<Exclusion>> = combine(
        _exclusions.flow,
        defaultExclusions.exclusions,
    ) { user, defaults ->
        val exclusions = mutableSetOf<Exclusion>()
        exclusions.addAll(user)
        defaults.forEach { def ->
            if (exclusions.none { it.id == def.id }) {
                log(TAG, VERBOSE) { "Injecting default $def" }
                exclusions.add(def)
            } else {
                log(TAG, VERBOSE) { "User exclusions overlap with $def" }
            }
        }
        exclusions
    }
        .shareIn(
            scope = appScope,
            started = SharingStarted.Lazily,
            replay = 1
        )


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

        defaultExclusions.remove(ids)
    }

    companion object {
        private val TAG = logTag("Exclusion", "Manager")
    }
}