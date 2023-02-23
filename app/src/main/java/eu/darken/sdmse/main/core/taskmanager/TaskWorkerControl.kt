package eu.darken.sdmse.main.core.taskmanager

import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskWorkerControl @Inject constructor(
    private val workerManager: WorkManager,
    private val dispatcherProvider: DispatcherProvider,
) {

    suspend fun startMonitor() {
        val workerData = Data.Builder().apply {

        }.build()
        log(TAG, VERBOSE) { "Worker data: $workerData" }

        val workRequest = OneTimeWorkRequestBuilder<TaskWorker>().apply {
            setInputData(workerData)
//            setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        }.build()

        log(TAG, VERBOSE) { "Worker request: $workRequest" }

        val operation = workerManager.enqueueUniqueWork(
            "${BuildConfigWrap.APPLICATION_ID}.taskmanager.worker",
            ExistingWorkPolicy.KEEP,
            workRequest,
        )

        operation.result.get()
        log(TAG) { "Worker start request send." }
    }

    companion object {
        val TAG = logTag("TaskManager", "Worker", "Control")
    }
}