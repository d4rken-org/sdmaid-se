package eu.darken.sdmse.common.review

import android.app.Activity
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FossReviewTool @Inject constructor() : ReviewTool {


    override val state: Flow<ReviewTool.State> = flowOf(ReviewTool.State())

    override suspend fun dismiss() {
        log(TAG, INFO) { "dismiss()" }
        // NOOP
    }

    override suspend fun reviewNow(activity: Activity) {
        log(TAG, INFO) { "reviewNow($activity)" }
        // NOOP
    }

    companion object {
        private val TAG = logTag("Review", "Tool", "FOSS")
    }
}