package eu.darken.sdmse.automation.core.animation

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AnimationState(
    @Json(name = "windowAnimationScale") val windowAnimationScale: Float?,
    @Json(name = "globalTransitionAnimationScale") val globalTransitionAnimationScale: Float?,
    @Json(name = "globalAnimatorDurationScale") val globalAnimatorDurationscale: Float?,
) {

    companion object {
        val DISABLED = AnimationState(
            windowAnimationScale = 0f,
            globalTransitionAnimationScale = 0f,
            globalAnimatorDurationscale = 0f,
        )
    }
}
