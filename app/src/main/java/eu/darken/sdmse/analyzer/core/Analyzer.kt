package eu.darken.sdmse.analyzer.core

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.progress.*
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.main.core.SDMTool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Analyzer @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
) : SDMTool, Progress.Client {

    override val sharedResource = SharedResource.createKeepAlive(TAG, appScope)

    private val progressPub = MutableStateFlow<Progress.Data?>(null)
    override val progress: Flow<Progress.Data?> = progressPub
    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    private val internalData = MutableStateFlow(null as Data?)
    val data: Flow<Data?> = internalData

    override val type: SDMTool.Type = SDMTool.Type.ANALYZER

    private val jobLock = Mutex()
    override suspend fun submit(task: SDMTool.Task): SDMTool.Task.Result = jobLock.withLock {
        task as AnalyzerTask
        log(TAG) { "submit($task) starting..." }
        updateProgress { Progress.DEFAULT_STATE }
        try {
            val result = when (task) {
                is AnalyzerScanTask -> performScan(task)
                else -> throw UnsupportedOperationException("Unsupported task: $task")
            }

            log(TAG, INFO) { "submit($task) finished: $result" }
            result
        } finally {
            updateProgress { null }
        }
    }

    private suspend fun performScan(task: AnalyzerScanTask): AnalyzerScanTask.Result {
        log(TAG, VERBOSE) { "performScan(): $task" }

        internalData.value = null

        internalData.value = Data()

        return AnalyzerScanTask.Result(
            itemCount = 0
        )
    }


    data class Data(
        val apps: Collection<Unit> = emptyList()
    )

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Analyzer): SDMTool
    }

    companion object {
        private val TAG = logTag("Analyzer")
    }
}