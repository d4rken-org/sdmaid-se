package eu.darken.sdmse.common.areas

import eu.darken.sdmse.common.areas.modules.DataAreaFactory
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class DataAreaManager @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val areaFactory: DataAreaFactory,
) {

    private val refreshGeneration = AtomicLong(0L)
    private val refreshTrigger = MutableStateFlow(refreshGeneration.get())
    private val _internalStateCache = MutableStateFlow<State?>(null)
    val latestState: Flow<State?> = _internalStateCache

    val state: Flow<State> = refreshTrigger
        .mapLatest { generation ->
            State(
                areas = areaFactory.build().toSet(),
                refreshGeneration = generation,
            )
        }
        .onEach { _internalStateCache.value = it }
        .setupCommonEventHandlers(TAG) { "state" }
        .shareIn(appScope, SharingStarted.Lazily, 1)

    suspend fun reload() {
        log(TAG, WARN) { "reload()" }
        if (_internalStateCache.value == null) {
            appScope.launch { state.first() }
        } else {
            triggerReload()
        }
    }

    suspend fun reloadAndAwait(): State {
        log(TAG, WARN) { "reloadAndAwait()" }
        val targetGeneration = triggerReload()
        return state.first { it.refreshGeneration >= targetGeneration }
    }

    private fun triggerReload(): Long = refreshGeneration.incrementAndGet().also {
        refreshTrigger.value = it
    }

    data class State(
        val areas: Set<DataArea>,
        val refreshGeneration: Long = 0L,
    )

    companion object {
        val TAG: String = logTag("DataArea", "Manager")
    }
}
