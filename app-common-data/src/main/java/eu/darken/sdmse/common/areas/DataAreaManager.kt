package eu.darken.sdmse.common.areas

import eu.darken.sdmse.common.areas.modules.DataAreaFactory
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.error.hasCause
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class DataAreaManager @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val areaFactory: DataAreaFactory,
) {

    private val refreshTrigger = MutableStateFlow(0L)
    private val _internalStateCache = MutableStateFlow<State?>(null)
    val latestState: Flow<State?> = _internalStateCache

    private val _latestResult = MutableStateFlow<Result<State>?>(null)

    /** Like [latestState], but a build failing from a cancellation replaces it. Neither starts a build. */
    val latestResult: Flow<Result<State>?> = _latestResult

    private val builds: Flow<Build> = refreshTrigger
        .mapLatest { generation ->
            val areas = try {
                Result.success(areaFactory.build().toSet())
            } catch (e: Exception) {
                // A build superseded by a newer reload is cancelled and must stay cancelled.
                currentCoroutineContext().ensureActive()
                // Rooted in a cancellation, this would end the flow without an error; see DataAreaManagerTest.
                if (!e.hasCause(CancellationException::class)) throw e
                log(TAG, ERROR) { "Building data areas failed: ${e.asLog()}" }
                // Thrown as-is, a CancellationException would read as the reader's own cancellation.
                val failure = if (e is CancellationException) IllegalStateException("Building data areas failed", e) else e
                Result.failure(failure)
            }
            Build(generation, areas)
        }
        .onEach { build ->
            val result = build.areas.map { State(it, build.generation) }
            result.onSuccess { _internalStateCache.value = it }
            _latestResult.value = result
        }
        .setupCommonEventHandlers(TAG) { "state" }
        .shareIn(appScope, SharingStarted.Lazily, 1)

    /**
     * Skips a state built before the latest reload, so reading it while a reload is still building waits for it. A
     * failed build arrives as a failure, and a collector keeps receiving the builds after it.
     */
    val results: Flow<Result<State>> = builds
        .filter { it.generation >= refreshTrigger.value }
        .map { build -> build.areas.map { State(areas = it, refreshGeneration = build.generation) } }

    /** [results] for one-shot reads: a failed build is thrown. */
    val state: Flow<State> = results.map { it.getOrThrow() }

    suspend fun reload() {
        log(TAG, WARN) { "reload()" }
        triggerReload()
        if (_internalStateCache.value == null) appScope.launch { builds.first() }
    }

    suspend fun reloadAndAwait(): State {
        log(TAG, WARN) { "reloadAndAwait()" }
        triggerReload()
        return state.first()
    }

    private fun triggerReload() = refreshTrigger.update { it + 1 }

    data class State(
        val areas: Set<DataArea>,
        val refreshGeneration: Long = 0L,
    )

    private data class Build(val generation: Long, val areas: Result<Set<DataArea>>)

    companion object {
        val TAG: String = logTag("DataArea", "Manager")
    }
}
