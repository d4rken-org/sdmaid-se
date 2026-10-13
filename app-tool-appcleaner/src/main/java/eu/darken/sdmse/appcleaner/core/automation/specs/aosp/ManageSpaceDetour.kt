package eu.darken.sdmse.appcleaner.core.automation.specs.aosp

import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import kotlinx.coroutines.delay

/**
 * A blind DPAD_CENTER that left Settings for the target app's own window pressed "Manage storage"
 * instead of "Clear cache". Settings itself as target is excluded, every Settings window would match.
 */
internal fun isManageSpaceDetour(
    centerSent: Boolean,
    windowPkg: String?,
    targetPkg: String,
): Boolean = centerSent &&
    windowPkg != null &&
    windowPkg == targetPkg &&
    targetPkg != AOSPSpecs.SETTINGS_PKG.name

/**
 * Mirrors GrapheneOS Settings' rule for showing "Manage storage" as a third storage button: Settings has
 * the `manage_storage_text` string resource and the app declares a manageSpaceActivity that resolves.
 */
internal fun predictsManageStorageButton(
    settingsHasManageStorageText: Boolean,
    manageSpaceActivityName: String?,
    manageSpaceActivityResolves: Boolean,
): Boolean = settingsHasManageStorageText &&
    manageSpaceActivityName != null &&
    manageSpaceActivityResolves

/**
 * Same row count as [original], and every row that was parseable there is parseable again.
 *
 * ```
 * original: ["12 MB", "3 MB"]  candidate: ["12 MB", "Calculating…"] -> false
 * original: ["12 MB", "0 o"]   candidate: ["12 MB", "0 o"]          -> true
 * ```
 */
internal fun StorageSnapshot.isSettledAgainst(original: StorageSnapshot): Boolean {
    if (values.size != original.values.size) return false
    return original.values.zip(values).all { (before, now) -> before.bytes == null || now.bytes != null }
}

internal class ManageSpaceDetourOps(
    val back: suspend () -> Boolean,
    val awaitAnchor: suspend () -> Boolean,
    val takeSnapshot: suspend () -> StorageSnapshot,
    val bootstrapAnchor: suspend () -> Boolean,
    val dpadDown: suspend () -> Boolean,
    val dpadRight: suspend () -> Boolean,
    val dpadLeft: suspend () -> Boolean,
    val focusVetoesCenter: suspend () -> Boolean,
    val dpadCenter: suspend () -> Boolean,
    val validate: suspend (baseline: StorageSnapshot) -> Boolean,
)

/**
 * Returns to the storage page and retries once with DOWN, RIGHT, LEFT: on a three-button row
 * DOWN+RIGHT reaches "Manage storage", one LEFT from there is "Clear cache".
 */
internal suspend fun recoverFromManageSpaceDetour(
    tag: String,
    ops: ManageSpaceDetourOps,
    originalSnapshot: StorageSnapshot,
    stepDelayMs: Long,
    settlePolls: Int = 10,
    settlePollDelayMs: Long = 200,
): Boolean {
    val source = MANAGE_SPACE_DETOUR_SOURCE

    val wentBack = ops.back()
    log(tag, INFO) { "GLOBAL_ACTION_BACK result=$wentBack (source=$source)" }
    if (!wentBack) return false

    val anchorRestored = ops.awaitAnchor()
    log(tag, INFO) { "Anchor restored=$anchorRestored (source=$source)" }
    if (!anchorRestored) return false

    var baseline: StorageSnapshot? = null
    for (poll in 1..settlePolls) {
        val candidate = ops.takeSnapshot()
        if (candidate.isSettledAgainst(originalSnapshot)) {
            baseline = candidate
            log(tag, INFO) { "Storage snapshot settled on poll $poll/$settlePolls (source=$source)" }
            break
        }
        if (poll < settlePolls) delay(settlePollDelayMs)
    }
    if (baseline == null) {
        log(tag, WARN) { "Storage snapshot never settled after $settlePolls polls (source=$source)" }
        return false
    }

    val bootstrapped = ops.bootstrapAnchor()
    log(tag, INFO) { "DPAD bootstrap result=$bootstrapped (source=$source)" }
    if (!bootstrapped) return false

    val keys = listOf(
        "DPAD_DOWN" to ops.dpadDown,
        "DPAD_RIGHT" to ops.dpadRight,
        "DPAD_LEFT" to ops.dpadLeft,
    )
    for ((name, key) in keys) {
        val sent = key()
        log(tag, INFO) { "$name result=$sent (source=$source)" }
        if (!sent) return false
        delay(stepDelayMs)
    }

    val veto = ops.focusVetoesCenter()
    log(tag, INFO) { "DPAD focus guard veto=$veto (source=$source)" }
    if (veto) {
        log(tag, WARN) { "DPAD_CENTER skipped, focus outside the button row (source=$source)" }
        return false
    }

    val clicked = ops.dpadCenter()
    log(tag, INFO) { "DPAD_CENTER result=$clicked (source=$source)" }
    if (!clicked) return false

    return ops.validate(baseline)
}

internal const val MANAGE_SPACE_DETOUR_SOURCE = "manage-space-detour"
