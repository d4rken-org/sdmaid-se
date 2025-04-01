package eu.darken.sdmse.automation.core.animation

data class AnimationState(
    val windowAnimationScale: Float?,
    val globalTransitionAnimationScale: Float?,
    val globalAnimatorDurationscale: Float?,
) {

    companion object {
        val DISABLED = AnimationState(
            windowAnimationScale = 0f,
            globalTransitionAnimationScale = 0f,
            globalAnimatorDurationscale = 0f,
        )
    }
}
