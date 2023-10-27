package eu.darken.sdmse.common.updater

import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import io.github.z4kn4fein.semver.Version

fun UpdateChecker.Update.isNewer(): Boolean = try {
    val current = Version.parse(BuildConfigWrap.VERSION_NAME, strict = false)
    val latest = Version.parse(versionName, strict = false)
    latest > current
} catch (e: Exception) {
    log(ERROR) { "Failed version check: ${e.asLog()}" }
    false
}

suspend fun UpdateChecker.getUpdate(): UpdateChecker.Update? {
    if (!isCheckSupported()) {
        log(TAG, INFO) { "Update check is not supported" }
        return null
    }

    val currentChannel = currentChannel()
    val update = getLatest(currentChannel)

    if (update == null) {
        log(TAG) { "No update available: ($currentChannel)" }
        return null
    }

    if (!update.isNewer()) {
        log(TAG) { "Latest update isn't newer: $update" }
        return null
    }

    if (isDismissed(update)) {
        log(TAG) { "Update was previously dismissed: $update" }
        return null
    }

    return update
}


private val TAG = logTag("Updater", "Checker")