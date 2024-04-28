package eu.darken.sdmse.common.review

import android.app.Activity
import kotlinx.coroutines.flow.Flow

interface ReviewTool {

    val state: Flow<State>

    data class State(
        val shouldAskForReview: Boolean = false,
        val hasReviewed: Boolean = false,
    )

    suspend fun dismiss()

    suspend fun reviewNow(activity: Activity)
}