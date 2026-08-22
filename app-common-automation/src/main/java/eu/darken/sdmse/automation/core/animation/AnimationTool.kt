package eu.darken.sdmse.automation.core.animation

import android.content.Context
import android.content.res.Resources
import android.provider.Settings
import android.util.TypedValue
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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

    private val txLock = Mutex()

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

    /**
     * The framework's own default for the transition scale, used when the setting row is absent.
     * [WINDOW_ANIMATION_SCALE] and [ANIMATOR_DURATION_SCALE] have no such resource, they default to [DEFAULT_SCALE].
     */
    private fun defaultTransitionScale(): Float = try {
        val resources = Resources.getSystem()
        val id = resources.getIdentifier(FRAMEWORK_TRANSITION_SCALE_DEFAULT, "dimen", "android")
        if (id == 0) {
            log(TAG) { "defaultTransitionScale(): No framework resource, using $DEFAULT_SCALE" }
            DEFAULT_SCALE
        } else {
            val typedValue = TypedValue()
            resources.getValue(id, typedValue, true)
            if (typedValue.type != TypedValue.TYPE_FLOAT) {
                // TypedValue.getFloat() reinterprets the raw data bits, that's only meaningful for TYPE_FLOAT
                log(TAG) { "defaultTransitionScale(): Not a float (type=${typedValue.type}), using $DEFAULT_SCALE" }
                DEFAULT_SCALE
            } else {
                val frameworkValue = typedValue.float
                if (!frameworkValue.isFinite() || frameworkValue <= 0f) {
                    log(TAG) { "defaultTransitionScale(): Unusable framework value ($frameworkValue), using $DEFAULT_SCALE" }
                    DEFAULT_SCALE
                } else {
                    log(TAG) { "defaultTransitionScale(): Using framework value $frameworkValue" }
                    frameworkValue
                }
            }
        }
    } catch (e: Exception) {
        log(TAG, WARN) { "defaultTransitionScale(): Lookup failed, using $DEFAULT_SCALE: ${e.asLog()}" }
        DEFAULT_SCALE
    }

    /**
     * Writes all three scales, substituting framework defaults for absent (null) values.
     * A null field means the setting row doesn't exist, so writing only the non-null fields would make
     * restoring an absent row a silent no-op. The write is verified by reading the values back.
     */
    suspend fun setState(state: AnimationState) {
        val intended = mapOf(
            WINDOW_ANIMATION_SCALE to (state.windowAnimationScale ?: DEFAULT_SCALE),
            TRANSITION_ANIMATION_SCALE to (state.globalTransitionAnimationScale ?: defaultTransitionScale()),
            ANIMATOR_DURATION_SCALE to (state.globalAnimatorDurationscale ?: DEFAULT_SCALE),
        )
        val result = shellOps.execute(
            ShellOpsCmd(intended.map { (key, value) -> getCommand(key, value) }),
            when {
                adbManager.canUseAdbNow() -> ShellOps.Mode.ADB
                rootManager.canUseRootNow() -> ShellOps.Mode.ROOT
                else -> throw IllegalStateException("No ShellOps Mode available to set animation state")
            }
        )
        log(TAG) { "setState($state) intended=$intended result: $result" }

        val readback = getState()
        val actual = mapOf(
            WINDOW_ANIMATION_SCALE to readback.windowAnimationScale,
            TRANSITION_ANIMATION_SCALE to readback.globalTransitionAnimationScale,
            ANIMATOR_DURATION_SCALE to readback.globalAnimatorDurationscale,
        )
        val mismatches = intended.filter { (key, value) -> actual[key] != value }
        if (mismatches.isNotEmpty()) {
            val details = mismatches.keys.joinToString { "$it (wanted ${intended[it]}, got ${actual[it]})" }
            throw IllegalStateException("Animation state was not applied: $details")
        }
    }

    suspend fun persistPendingState(state: AnimationState) {
        log(TAG) { "persistPendingState($state)" }
        animationSettings.animationPendingRestoreState.value(state)
    }

    suspend fun clearPendingState() {
        log(TAG) { "clearPendingState()" }
        animationSettings.animationPendingRestoreState.value(null)
    }

    enum class RestoreResult {
        NOTHING_PENDING,
        RESTORED,
        FAILED,
        ;
    }

    suspend fun restorePendingState(): RestoreResult = txLock.withLock {
        val pending = animationSettings.animationPendingRestoreState.value()
        if (pending == null) {
            log(TAG, VERBOSE) { "restorePendingState(): No pending animation state" }
            return@withLock RestoreResult.NOTHING_PENDING
        }

        log(TAG, INFO) { "Found pending animation state to restore: $pending" }

        if (!canChangeState()) {
            log(TAG, WARN) { "Cannot restore pending animation state: no shell access available" }
            return@withLock RestoreResult.FAILED
        }

        try {
            setState(pending)
            clearPendingState()
            log(TAG, INFO) { "Successfully restored pending animation state" }
            RestoreResult.RESTORED
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to restore pending animation state: ${e.asLog()}" }
            RestoreResult.FAILED
        }
    }

    /**
     * Captures the current state, persists it as pending restore state, disables animations, runs [block] and
     * restores the captured state afterwards, no matter how [block] ends.
     *
     * [txLock] is held for the whole duration, including [block]. That's what keeps a concurrent
     * [restorePendingState] from re-enabling animations mid-task or clearing the pending record while we still
     * need it. Consequently nothing inside [block] may call a [txLock]-guarded method ([restorePendingState],
     * [withAnimationsDisabled]), a [Mutex] is not reentrant and would deadlock. [getState] is unguarded and safe.
     *
     * Failing to disable animations is not fatal, [block] runs regardless. The restore runs [NonCancellable] so a
     * cancellation, including one hitting the disable write itself, still leaves the device with its original state.
     */
    suspend fun <R> withAnimationsDisabled(block: suspend () -> R): R = txLock.withLock {
        val previous = getState()
        persistPendingState(previous)
        try {
            try {
                setState(AnimationState.DISABLED)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, WARN) { "withAnimationsDisabled(): Failed to disable animations: ${e.asLog()}" }
            }
            block()
        } finally {
            withContext(NonCancellable) {
                var restored = false
                try {
                    setState(previous)
                    restored = true
                } catch (e: Exception) {
                    log(TAG, ERROR) { "withAnimationsDisabled(): Failed to restore animation state: ${e.asLog()}" }
                }
                if (restored) {
                    try {
                        clearPendingState()
                    } catch (e: Exception) {
                        log(TAG, ERROR) { "withAnimationsDisabled(): Failed to clear pending state: ${e.asLog()}" }
                    }
                }
            }
        }
    }

    companion object {
        private const val WINDOW_ANIMATION_SCALE = "window_animation_scale"
        private const val TRANSITION_ANIMATION_SCALE = "transition_animation_scale"
        private const val ANIMATOR_DURATION_SCALE = "animator_duration_scale"
        private const val FRAMEWORK_TRANSITION_SCALE_DEFAULT = "config_appTransitionAnimationDurationScaleDefault"
        private const val DEFAULT_SCALE = 1.0f
        val TAG: String = logTag("Automation", "AnimationTool")
    }
}
