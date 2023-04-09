package eu.darken.sdmse.common.updater

import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import io.github.z4kn4fein.semver.Version

fun UpdateChecker.Update.isNewer(): Boolean = try {
    val current = Version.parse(BuildConfigWrap.VERSION_NAME, strict = false)
    val latest = Version.parse(versionName, strict = false)
    latest > current
} catch (e: Exception) {
    log(ERROR) { "Failed version check: ${e.asLog()}" }
    false
}