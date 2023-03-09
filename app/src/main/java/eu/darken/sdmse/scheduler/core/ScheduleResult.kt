package eu.darken.sdmse.scheduler.core

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.Instant

@JsonClass(generateAdapter = true)
data class ScheduleResult(
    @Json(name = "schedule_id") val scheduleId: String,
    @Json(name = "executed_at") val executedAt: Instant,
    @Json(name = "space_freed") val spaceFreed: Long?,
    @Json(name = "success_message") val successMessage: String?,
    @Json(name = "error_message") val errorMessage: String?,
)
