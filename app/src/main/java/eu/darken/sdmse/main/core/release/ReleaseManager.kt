package eu.darken.sdmse.main.core.release

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.getPackageInfo
import eu.darken.sdmse.main.core.CurriculumVitae
import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.VersionFormatException
import kotlinx.coroutines.flow.firstOrNull
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReleaseManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: ReleaseSettings,
    private val curriculumVitae: CurriculumVitae,
) {

    private val ourVersion: Version by lazy {
        val versionName = context.getPackageInfo().versionName!!
        try {
            Version.parse(versionName, strict = false).also { log(TAG) { "Current version is $it" } }
        } catch (e: VersionFormatException) {
            log(TAG, ERROR) { "Version parsing failed for $versionName" }
            Version(0, 0, 0)
        }
    }

    suspend fun hasBetaConsent() = if (ourVersion >= CUTOFF) settings.wantsBeta.value() else true

    suspend fun releaseParty(): Boolean {
        if (settings.releasePartyAt.value() != null) {
            log(TAG) { "releaseParty(): Already had a party (wantsBeta=${settings.wantsBeta.value()})" }
            return false
        }

        val updateHistory = curriculumVitae.history.firstOrNull()
        log(TAG) { "releaseParty(): Checking via update history: $updateHistory" }
        if (updateHistory.isNullOrEmpty()) return false

        val usedBetaBeforeCutoff = updateHistory.any { it < CUTOFF }

        return when {
            ourVersion < CUTOFF -> {
                log(TAG, INFO) { "releaseParty(): Before CUTOFF, no party yet." }
                false
            }

            ourVersion >= CUTOFF && usedBetaBeforeCutoff -> {
                log(TAG, INFO) { "releaseParty(): Party time" }
                true
            }

            else -> {
                log(TAG, INFO) { "releaseParty(): User isn't invited." }
                settings.releasePartyAt.value(Instant.now())
                false
            }
        }
    }

    suspend fun checkEarlyAdopter() {
        val earlyAdopter = settings.earlyAdopter.value()
        if (earlyAdopter != null) {
            log(TAG) { "checkEarlyAdopter(): State already set ($earlyAdopter)" }
            return
        }
        val updateHistory = curriculumVitae.history.firstOrNull()
        if (updateHistory.isNullOrEmpty()) {
            log(TAG) { "checkEarlyAdopter(): Update history is empty, not an early adopter." }
            settings.earlyAdopter.value(false)
            return
        }

        val hasHadBeta = updateHistory.any { it < CUTOFF }
        log(TAG) { "checkEarlyAdopter(): Has had beta installed before cutoff? earlyAdopter=$hasHadBeta" }
        settings.earlyAdopter.value(hasHadBeta)
    }

    companion object {
        private val CUTOFF = Version(1, 0, 0, "rc0")
        private val TAG = logTag("Release", "Manager")
    }
}