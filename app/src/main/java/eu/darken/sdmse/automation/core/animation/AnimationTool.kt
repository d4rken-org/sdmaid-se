package eu.darken.sdmse.automation.core.animation

import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.automation.core.AutomationSettings
import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.adb.canUseAdbNow
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
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
    @ApplicationContext private val context: Context,
    private val adbManager: AdbManager,
    private val rootManager: RootManager,
    private val shellOps: ShellOps,
    private val animationSettings: AutomationSettings,
) {
    suspend fun canChangeState(): Boolean {
        val adb = adbManager.canUseAdbNow()
        val root = rootManager.canUseRootNow()
        log(TAG, VERBOSE) { "canChangeState(): adb=$adb root=$root" }
        return adb || root
    }

    private fun tryGetFloat(key: String): Float? =
        Settings.Global.getFloat(context.contentResolver, key, Float.MIN_VALUE).takeIf { it != Float.MIN_VALUE }

    suspend fun getState(): AnimationState = AnimationState(
        windowAnimationScale = tryGetFloat(WINDOW_ANIMATION_SCALE),
        globalTransitionAnimationScale = tryGetFloat(TRANSITION_ANIMATION_SCALE),
        globalAnimatorDurationscale = tryGetFloat(ANIMATOR_DURATION_SCALE),
    ).also { log(TAG) { "getState(): $it" } }

    private fun getCommand(key: String, value: Float): String = "settings put global $key $value"

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

    suspend fun persistPendingState(state: AnimationState) {
        log(TAG) { "persistPendingState($state)" }
        animationSettings.animationPendingRestoreState.value(state)
    }

    suspend fun clearPendingState() {
        log(TAG) { "clearPendingState()" }
        animationSettings.animationPendingRestoreState.value(null)
    }

    suspend fun restorePendingState(): Boolean {
        val pending = animationSettings.animationPendingRestoreState.value() ?: return false

        log(TAG, INFO) { "Found pending animation state to restore: $pending" }

        if (!canChangeState()) {
            log(TAG, WARN) { "Cannot restore pending animation state: no shell access available" }
            return false
        }

        return try {
            setState(pending)
            clearPendingState()
            log(TAG, INFO) { "Successfully restored pending animation state" }
            true
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to restore pending animation state: ${e.asLog()}" }
            false
        }
    }

    companion object {
        private const val WINDOW_ANIMATION_SCALE = "window_animation_scale"
        private const val TRANSITION_ANIMATION_SCALE = "transition_animation_scale"
        private const val ANIMATOR_DURATION_SCALE = "animator_duration_scale"
        val TAG: String = logTag("Automation", "AnimationTool")
    }
}
