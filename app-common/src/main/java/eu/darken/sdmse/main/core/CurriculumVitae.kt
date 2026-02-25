package eu.darken.sdmse.main.core

import android.content.Context
import android.content.pm.PackageInfo
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.createValue
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.datastore.valueBlocking
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.getPackageInfo
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.VersionFormatException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

@Singleton
class CurriculumVitae @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope,
    moshi: Moshi,
    private val upgradeRepo: UpgradeRepo,
    private val dispatcherProvider: DispatcherProvider,
) {

    private val Context.dataStore by preferencesDataStore(name = "curriculum_vitae")

    private val dataStore: DataStore<Preferences>
        get() = context.dataStore

    private val usPkgInfo: PackageInfo by lazy { context.getPackageInfo() }

    private val _updateHistory = dataStore.createValue("stats.update.history", emptyList<String>(), moshi)
    private val _installedFirst = dataStore.createValue<Instant?>("stats.install.first", null, moshi)
    val installedAt = _installedFirst.flow.filterNotNull()
    private val _launchedLast = dataStore.createValue<Instant?>("stats.launched.last", null, moshi)
    private val _launchedCount = dataStore.createValue("stats.launched.count", 0)
    private val _launchedCountBeta = dataStore.createValue("stats.launched.beta.count", 0)

    fun updateAppLaunch() = appScope.launch(dispatcherProvider.IO) {
        log(TAG, VERBOSE) { "updateAppLaunch()" }
        updateInstalledAt()
        updateLaunchTime()
        updateLaunchCount()
        if (BuildConfigWrap.BUILD_TYPE != BuildConfigWrap.BuildType.RELEASE) {
            updateLaunchCountBeta()
        }
        updateVersionHistory()
    }

    private suspend fun updateInstalledAt() {
        val savedInstalledAt = _installedFirst.value()
        log(TAG, VERBOSE) { "updateInstalledAt(): saved: $savedInstalledAt" }

        val systemInstalledAt = usPkgInfo.firstInstallTime.let { Instant.ofEpochMilli(it) }
        log(TAG, VERBOSE) { "updateInstalledAt(): system: $systemInstalledAt" }

        val upgradedAt = try {
            upgradeRepo.upgradeInfo
                .filter { !it.isPro || it.upgradedAt != null }
                .timeout(5.seconds)
                .first()
                .upgradedAt
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to get upgrade info: ${e.asLog()}" }
            null
        }
        log(TAG, VERBOSE) { "updateInstalledAt(): upgradedAt: $upgradedAt" }

        when {
            upgradedAt != null && (savedInstalledAt == null || upgradedAt < savedInstalledAt) -> {
                log(TAG, INFO) { "updateInstalledAt(): This is a re-install, setting install date to upgrade date." }
                _installedFirst.value(upgradedAt)
            }

            savedInstalledAt == null -> {
                log(TAG) { "updateInstalledAt(): Saving install time: $systemInstalledAt" }
                _installedFirst.value(systemInstalledAt)
            }

            else -> {
                log(TAG, VERBOSE) { "updateInstalledAt(): Keeping current install date." }
            }
        }
    }

    private suspend fun updateLaunchTime() {
        val oldLaunchTime = _launchedLast.value()
        log(TAG) { "Last launch time was $oldLaunchTime" }
        _launchedLast.value(Instant.now())
    }

    private suspend fun updateLaunchCount() {
        val newLaunchCount = _launchedCount.value() + 1
        log(TAG) { "Launch count is $newLaunchCount" }
        _launchedCount.value(newLaunchCount)
    }

    private suspend fun updateLaunchCountBeta() {
        val newLaunchCount = _launchedCountBeta.value() + 1
        log(TAG) { "Launch BETA count is $newLaunchCount" }
        _launchedCountBeta.value(newLaunchCount)
    }

    val history = _updateHistory.flow.map { versions ->
        versions.mapNotNull {
            try {
                Version.parse(it, false)
            } catch (_: VersionFormatException) {
                log(TAG, WARN) { "Invalid version format: $it out of $versions" }
                null
            }
        }
    }

    private suspend fun updateVersionHistory() {
        val history = _updateHistory.value()
        log(TAG) { "Current version history is $history" }

        val lastVersion = history.lastOrNull()
        val current = usPkgInfo.versionName!!
        if (lastVersion != current) {
            val versionHistory = history + current
            log(TAG) { "Update happened, new version history is $versionHistory" }
            _updateHistory.value(versionHistory)
        }
    }

    private val _openedLast = dataStore.createValue<Instant?>("stats.opened.last", null, moshi)
    private val _openedCount = dataStore.createValue("stats.opened.count", 0)

    fun updateAppOpened() = appScope.launch {
        log(TAG, VERBOSE) { "updateAppOpened()" }
        updateOpenedTime()
        updateOpenedCount()
    }

    private suspend fun updateOpenedTime() {
        val oldOpenedTime = _openedLast.value()
        log(TAG) { "Last open was $oldOpenedTime" }
        _openedLast.value(Instant.now())
    }

    private suspend fun updateOpenedCount() {
        val newOpenedcount = _openedCount.value() + 1
        log(TAG) { "Open count is $newOpenedcount" }
        _openedCount.value(newOpenedcount)
    }

    fun isAnniversary(): Boolean {
        val installedAt = _installedFirst.valueBlocking ?: return false
        val now = LocalDate.now()
        val installDate = LocalDate.ofInstant(installedAt, ZoneId.systemDefault())

        // Check if it's been at least one year
        val yearsSinceInstall = Period.between(installDate, now).years
        if (yearsSinceInstall < 1) return false

        // Calculate this year's anniversary date
        val thisYearAnniversary = installDate.withYear(now.year)
        val lastYearAnniversary = installDate.withYear(now.year - 1)

        // Check if we're near an anniversary (within 14 days after)
        val daysFromThisYear = ChronoUnit.DAYS.between(thisYearAnniversary, now)
        val daysFromLastYear = ChronoUnit.DAYS.between(lastYearAnniversary, now)

        // Show if:
        // - 0 to 14 days after this year's anniversary
        // - OR 0 to 14 days after last year's anniversary (for early January)
        return (daysFromThisYear >= 0 && daysFromThisYear <= 14) ||
                (daysFromLastYear >= 0 && daysFromLastYear <= 14)
    }

    fun getYearsSinceInstall(): Int {
        val installedAt = _installedFirst.valueBlocking ?: return 0
        val installDate = LocalDate.ofInstant(installedAt, ZoneId.systemDefault())
        val now = LocalDate.now()
        return Period.between(installDate, now).years
    }

    companion object {
        internal val TAG = logTag("Debug", "CurriculumVitae")
    }
}