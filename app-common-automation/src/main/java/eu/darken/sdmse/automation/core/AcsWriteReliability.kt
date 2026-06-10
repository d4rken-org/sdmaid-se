package eu.darken.sdmse.automation.core

import dagger.Reusable
import eu.darken.sdmse.common.BuildWrap
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import javax.inject.Inject

/**
 * Remembers, per OS build, whether writing `enabled_accessibility_services` from our own process is
 * unreliable (silently reverted, or persisted but never bound). The marker is keyed to
 * [BuildWrap.FINGERPRINT] so an OTA (which changes the fingerprint) automatically re-probes the direct
 * path instead of staying stuck on the shell fallback forever. Observed on WAIPU TV (Android 14).
 */
@Reusable
class AcsWriteReliability @Inject constructor(
    private val settings: AutomationSettings,
) {

    suspend fun shouldAvoidDirectWrite(): Boolean =
        settings.acsDirectWriteUnreliableFingerprint.value() == BuildWrap.FINGERPRINT

    suspend fun markDirectWriteUnreliable() {
        if (shouldAvoidDirectWrite()) return
        log(TAG, WARN) { "markDirectWriteUnreliable(): Direct ACS write flagged unreliable for ${BuildWrap.FINGERPRINT}" }
        settings.acsDirectWriteUnreliableFingerprint.value(BuildWrap.FINGERPRINT)
    }

    companion object {
        private val TAG = logTag("Automation", "AcsWriteReliability")
    }
}
