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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExclusionManager @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    private val exclusionStorage: ExclusionStorage,
    private val defaultExclusions: DefaultExclusions,
) {

    // Serializes mutations across the two backing stores (user storage + defaults DataStore) so a
    // concurrent save/remove/restore can't interleave and leave them incoherent.
    private val mutex = Mutex()

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

    suspend fun save(toSave: Set<Exclusion>): Collection<Exclusion> = mutex.withLock {
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
        newOrUpdated
    }

    suspend fun remove(ids: Set<ExclusionId>) = mutex.withLock {
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

        // Only the default IDs actually targeted by this call — otherwise removing a user exclusion
        // would pollute the removed-defaults set with that user ID (the pre-v0.23.3-beta0 bug).
        val defaultTargets = defaultExclusions.exclusions.first()
            .filter { ids.contains(it.id) }
            .map { it.id }
            .toSet()
        if (defaultTargets.isNotEmpty()) {
            log(TAG) { "remove(): Removing default exclusions: $defaultTargets" }
            defaultExclusions.remove(defaultTargets)
        }

        val unknown = ids - userTargets.map { it.id }.toSet() - defaultTargets
        if (unknown.isNotEmpty()) {
            log(TAG, WARN) { "remove(): Unknown IDs, can't remove $unknown" }
        }
    }

    /**
     * Atomically replaces ALL user exclusions with [exclusions] (used by config restore in REPLACE
     * mode). Single mutex acquisition so a concurrent scan never observes a transient empty set.
     */
    suspend fun replaceUserExclusions(exclusions: Set<Exclusion>) = mutex.withLock {
        log(TAG, INFO) { "replaceUserExclusions(${exclusions.size})" }
        userExclusions.updateBlocking {
            exclusions.also { exclusionStorage.save(it) }
        }
    }

    /**
     * Restores the built-in default exclusions to their pristine state: un-removes any deleted
     * defaults AND drops user exclusions that shadow a built-in default (same ID, different tags).
     */
    suspend fun restoreDefaults() = mutex.withLock {
        log(TAG, INFO) { "restoreDefaults()" }
        val ids = defaultExclusions.defaultIds
        val shadowing = userExclusions.flow.first().filter { ids.contains(it.id) }.toSet()
        if (shadowing.isNotEmpty()) {
            log(TAG) { "restoreDefaults(): Dropping shadowing user exclusions: $shadowing" }
            userExclusions.updateBlocking {
                (this - shadowing).also { exclusionStorage.save(it) }
            }
        }
        defaultExclusions.reset()
    }

    companion object {
        private val TAG = logTag("Exclusion", "Manager")
    }
}