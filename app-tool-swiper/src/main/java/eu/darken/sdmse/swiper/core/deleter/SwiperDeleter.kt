package eu.darken.sdmse.swiper.core.deleter

import android.content.Context
import android.media.MediaScannerConnection
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.delete
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.updateProgressCount
import eu.darken.sdmse.common.progress.updateProgressPrimary
import eu.darken.sdmse.common.progress.updateProgressSecondary
import eu.darken.sdmse.swiper.core.SwipeDecision
import eu.darken.sdmse.swiper.core.SwipeItem
import eu.darken.sdmse.swiper.core.db.SwipeItemDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

class SwiperDeleter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gatewaySwitch: GatewaySwitch,
) : Progress.Host, Progress.Client {

    private val progressPub = MutableStateFlow<Progress.Data?>(Progress.Data())
    override val progress: Flow<Progress.Data?> = progressPub.throttleLatest(250)

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    data class Result(
        val deletedPaths: Set<APath>,
        val deletedSize: Long,
        val failedPaths: Set<APath>,
    )

    suspend fun delete(items: List<SwipeItem>, itemDao: SwipeItemDao): Result {
        log(TAG) { "delete(items=${items.size})" }

        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_deleting)

        val deletedPaths = mutableSetOf<APath>()
        val failedPaths = mutableSetOf<APath>()
        var deletedSize = 0L

        items.forEachIndexed { index, item ->
            updateProgressSecondary(item.lookup.userReadablePath)
            updateProgressCount(Progress.Count.Percent(index, items.size))

            if (Bugs.isDryRun) {
                log(TAG, INFO) { "DRYRUN: Not deleting ${item.lookup.lookedUp}" }
            } else {
                try {
                    item.lookup.lookedUp.delete(gatewaySwitch)
                    deletedPaths.add(item.lookup.lookedUp)
                    deletedSize += item.lookup.size
                    log(TAG, VERBOSE) { "Deleted: ${item.lookup.lookedUp}" }

                    // Mark item as DELETED
                    itemDao.updateDecision(item.id, SwipeDecision.DELETED)

                    // Notify MediaScanner about deleted file
                    notifyMediaScanner(item.lookup.lookedUp)
                } catch (e: Exception) {
                    log(TAG, WARN) { "Failed to delete ${item.lookup.lookedUp}: ${e.message}" }
                    failedPaths.add(item.lookup.lookedUp)

                    // Mark item as DELETE_FAILED
                    itemDao.updateDecision(item.id, SwipeDecision.DELETE_FAILED)
                }
            }
        }

        log(TAG) { "Deletion complete: deleted=${deletedPaths.size}, failed=${failedPaths.size}, size=$deletedSize" }

        return Result(
            deletedPaths = deletedPaths,
            deletedSize = deletedSize,
            failedPaths = failedPaths,
        )
    }

    private fun notifyMediaScanner(path: APath) {
        when (path.pathType) {
            APath.PathType.LOCAL ->
                try {
                    val localPath = path as LocalPath
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(localPath.file.absolutePath),
                        null,
                        null,
                    )
                } catch (e: Exception) {
                    log(TAG, WARN) { "MediaScanner notification failed for $path: ${e.message}" }
                }
            APath.PathType.SAF -> {
                log(TAG, WARN) { "Notifying media scanner about SAF changes is not supported atm." }
                // TODO implement this?
            }
            APath.PathType.RAW -> throw IllegalStateException("How did we get $path here")
        }
    }

    companion object {
        private val TAG = logTag("Swiper", "Deleter")
    }
}
