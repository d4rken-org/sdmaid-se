package eu.darken.sdmse.corpsefinder.core

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.withProgress
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.corpsefinder.core.filter.CorpseFilter
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderDeleteTask
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderScanTask
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderTask
import eu.darken.sdmse.main.core.SDMTool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CorpseFinder @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val filters: Set<@JvmSuppressWildcards CorpseFilter>,
) : SDMTool, Progress.Client {
    override val sharedResource = SharedResource.createKeepAlive(TAG, appScope)

    private val progressPub = MutableStateFlow<Progress.Data?>(null)
    override val progress: Flow<Progress.Data?> = progressPub
    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    private val internalData = MutableStateFlow(null as Data?)
    val data: Flow<Data?> = internalData

    override val type: SDMTool.Type = SDMTool.Type.CORPSEFINDER

    private val jobLock = Mutex()
    override suspend fun submit(task: SDMTool.Task): SDMTool.Task.Result = jobLock.withLock {
        task as CorpseFinderTask
        log(TAG) { "submit($task) starting..." }
        try {
            val result = useSharedResource {
                when (task) {
                    is CorpseFinderDeleteTask -> deleteCorpse(task)
                    is CorpseFinderScanTask -> performScan(task)
                }
            }
            log(TAG, INFO) { "submit($task) finished: $result" }
            result
        } finally {

        }
    }

    private suspend fun performScan(task: CorpseFinderScanTask): CorpseFinderTask.Result = try {
        val scanStart = System.currentTimeMillis()

        val result = filters
            .onEach { it.addParent(this@CorpseFinder) }
            .map { filter ->
                currentCoroutineContext()
                filter.withProgress(this@CorpseFinder) {
                    scan()
                }
            }
            .flatten()

        internalData.value = Data(
            corpses = result
        )

        val scanStop = System.currentTimeMillis()
        val time = Duration.ofMillis(scanStop - scanStart)
        CorpseFinderScanTask.Success(
            duration = time
        )
    } catch (e: Exception) {
        log(TAG, ERROR) { "performScan($task) failed: ${e.asLog()}" }
        CorpseFinderScanTask.Error(e)
    }

    private suspend fun deleteCorpse(task: CorpseFinderDeleteTask): CorpseFinderTask.Result = try {
        CorpseFinderDeleteTask.Success(TODO())
    } catch (e: Exception) {
        log(TAG, ERROR) { "performScan($task) failed: ${e.asLog()}" }
        CorpseFinderDeleteTask.Error(e)
    }

    data class Data(
        val corpses: Collection<Corpse>
    ) {
        val totalSize: Long
            get() = corpses.sumOf { it.size }
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: CorpseFinder): SDMTool
    }

    companion object {
        private val TAG = logTag("CorpseFinder")
    }
}