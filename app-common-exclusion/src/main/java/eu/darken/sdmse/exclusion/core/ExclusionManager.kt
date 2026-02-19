package eu.darken.sdmse.exclusion.core

import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.DynamicStateFlow
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.ExclusionHolder
import eu.darken.sdmse.exclusion.core.types.ExclusionId
import eu.darken.sdmse.exclusion.core.types.UserExclusion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    private val userExclusions = DynamicStateFlow(parentScope = appScope + dispatcherProvider.IO) {
        (exclusionStorage.load() ?: emptySet()).also {
            log(TAG) { "Initialized with ${it.size} user exclusions:\n${it.joinToString("\n")}" }
        }
    }

    val exclusions: Flow<Collection<ExclusionHolder>> = combine(
        userExclusions.flow,
        defaultExclusions.exclusions,
    ) { user, defaults ->
        val combined = mutableSetOf<ExclusionHolder>()

        defaults
            .filter { def ->
                val notCovered = user.none { it.id == def.id }
                if (!notCovered) log(TAG, WARN) { "User exclusions overlap with $def" }
                notCovered
            }
            .run { combined.addAll(this) }

        user
            .map { UserExclusion(exclusion = it) }
            .run { combined.addAll(this) }

        combined.also {
            log(TAG, INFO) { "Exclusions (${it.size}) are:\n${it.joinToString("\n")}" }
        }
    }
        .setupCommonEventHandlers(TAG) { "exclusions" }
        .shareIn(
            scope = appScope,
            started = SharingStarted.Lazily,
            replay = 1
        )

    suspend fun save(toSave: Set<Exclusion>): Collection<Exclusion> {
        log(TAG) { "save(): $toSave" }
        val newOrUpdated = mutableSetOf<Exclusion>()
        userExclusions.updateBlocking {
            val newExclusions = toSave.filter {
                val isDupe = this.contains(it)
                if (isDupe) log(TAG) { "Exclusion already exists: $it" }
                else newOrUpdated.add(it)
                !isDupe
            }
            this
                .filter { old -> newExclusions.none { old.id == it.id } }
                .plus(newExclusions).toSet()
                .also { exclusionStorage.save(it) }
        }
        return newOrUpdated
    }

    suspend fun remove(ids: Set<ExclusionId>) {
        log(TAG, INFO) { "remove($ids)..." }

        val userTargets = userExclusions.flow.first().filter { ids.contains(it.id) }.toSet()
        if (userTargets.isNotEmpty()) {
            log(TAG) { "remove(): Removing user exclusions: $userTargets" }
            userExclusions.updateBlocking {
                (this - userTargets).also {
                    exclusionStorage.save(it)
                }
            }
        }

        val defaultTargets = defaultExclusions.exclusions.first()
        if (defaultTargets.isNotEmpty()) {
            log(TAG) { "remove(): Removing defaults exclusion: $defaultTargets" }
            defaultExclusions.remove(ids)
        }

        if (userTargets.isEmpty() && defaultTargets.isEmpty()) {
            log(TAG, WARN) { "remove(): Unknown IDs, can't remove $ids" }
        }
    }

    companion object {
        private val TAG = logTag("Exclusion", "Manager")
    }
}