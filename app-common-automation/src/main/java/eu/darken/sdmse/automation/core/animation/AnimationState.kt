package eu.darken.sdmse.automation.core.animation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnimationState(
    @SerialName("windowAnimationScale") val windowAnimationScale: Float?,
    @SerialName("globalTransitionAnimationScale") val globalTransitionAnimationScale: Float?,
    @SerialName("globalAnimatorDurationScale") val globalAnimatorDurationscale: Float?,
) {

    companion object {
        val DISABLED = AnimationState(
            windowAnimationScale = 0f,
            globalTransitionAnimationScale = 0f,
            globalAnimatorDurationscale = 0f,
        )
    }
}
