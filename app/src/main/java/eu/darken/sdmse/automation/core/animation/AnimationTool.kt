package eu.darken.sdmse.automation.core.animation

import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.adb.canUseAdbNow
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.canUseRootNow
import eu.darken.sdmse.common.shell.ShellOps
import eu.darken.sdmse.common.shell.ipc.ShellOpsCmd
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnimationTool @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val adbManager: AdbManager,
    private val rootManager: RootManager,
    private val shellOps: ShellOps,
) {
    suspend fun canChangeState(): Boolean {
        val adb = adbManager.canUseAdbNow()
        val root = rootManager.canUseRootNow()
        log(TAG, VERBOSE) { "canChangeState(): adb=$adb root=$rootManager" }
        return adb || root
    }

    private fun tryGetFloat(key: String): Float? =
        Settings.Global.getFloat(context.contentResolver, key, Float.MIN_VALUE).takeIf { it != Float.MIN_VALUE }

    suspend fun getState(): AnimationState = AnimationState(
        windowAnimationScale = tryGetFloat(WINDOW_ANIMATION_SCALE),
        globalTransitionAnimationScale = tryGetFloat(TRANSITION_ANIMATION_SCALE),
        globalAnimatorDurationscale = tryGetFloat(ANIMATOR_DURATION_SCALE),
    ).also { log(TAG) { "getState(): $it" } }

    private fun getCommand(key: String, value: Any): String = "settings put global $key $value"

    suspend fun setState(state: AnimationState) {
        val cmds = mutableListOf<String>()
        state.windowAnimationScale?.let { cmds.add(getCommand(WINDOW_ANIMATION_SCALE, it)) }
        state.globalTransitionAnimationScale?.let { cmds.add(getCommand(TRANSITION_ANIMATION_SCALE, it)) }
        state.globalAnimatorDurationscale?.let { cmds.add(getCommand(ANIMATOR_DURATION_SCALE, it)) }
        val result = shellOps.execute(
            ShellOpsCmd(cmds),
            when {
                adbManager.canUseAdbNow() -> ShellOps.Mode.ADB
                rootManager.canUseRootNow() -> ShellOps.Mode.ROOT
                else -> throw IllegalStateException("No ShellOps Mode available to set animation state")
            }
        )
        log(TAG) { "setState($state) result: $result" }
    }

    companion object {
        private const val WINDOW_ANIMATION_SCALE = "window_animation_scale"
        private const val TRANSITION_ANIMATION_SCALE = "transition_animation_scale"
        private const val ANIMATOR_DURATION_SCALE = "animator_duration_scale"
        val TAG: String = logTag("Automation", "AnimationTool")
    }
}