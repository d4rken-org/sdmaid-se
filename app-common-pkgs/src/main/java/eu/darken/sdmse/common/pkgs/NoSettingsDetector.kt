package eu.darken.sdmse.common.pkgs

import dagger.Reusable
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.features.Installed
import javax.inject.Inject

/**
 * Single seam for detecting packages whose Android Settings details page can't be opened.
 * Currently delegates to [Installed.hasNoSettings] (structural APEX/mainline check).
 * Designed as an injectable so smarter detection can replace the implementation later
 * without touching call sites.
 */
@Reusable
class NoSettingsDetector @Inject constructor() {

    fun hasNoSettings(pkg: Installed): Boolean {
        val skip = pkg.hasNoSettings
        if (skip) log(TAG, VERBOSE) { "hasNoSettings=true for ${pkg.installId}" }
        return skip
    }

    companion object {
        private val TAG = logTag("Pkg", "NoSettingsDetector")
    }
}
