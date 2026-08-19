package eu.darken.sdmse.analyzer.core.backup

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import eu.darken.sdmse.analyzer.core.AnalyzerSettings
import eu.darken.sdmse.common.backup.RestoreMode
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testhelpers.BaseTest
import java.nio.file.Path

class AnalyzerSettingsBackupContributorTest : BaseTest() {

    @Test
    fun `the armed latch is excluded from a backup snapshot`(@TempDir tempDir: Path) = runTest {
        // The latch is runtime state: restoring armed=false onto a fresh install would suppress
        // that device's first low-space warning indefinitely.
        val store = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { tempDir.resolve("analyzer.preferences_pb").toFile() },
        )
        store.edit {
            it[booleanPreferencesKey("storage.low.notification.enabled")] = true
            it[booleanPreferencesKey("storage.low.notification.armed")] = false
            it[booleanPreferencesKey("hint.lowspace.dismissed")] = true
            it[longPreferencesKey("storage.low.threshold.bytes")] = 10_000_000_000L
        }
        val settings = mockk<AnalyzerSettings>().apply {
            every { dataStore } returns store
        }

        val snapshot = AnalyzerSettingsBackupContributor(settings).snapshot()!!

        snapshot.jsonObject.keys shouldBe setOf(
            "storage.low.notification.enabled",
            "hint.lowspace.dismissed",
            "storage.low.threshold.bytes",
        )
    }

    @Test
    fun `restoring never overwrites the on-device latch`(@TempDir tempDir: Path) = runTest {
        val source = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { tempDir.resolve("source.preferences_pb").toFile() },
        )
        source.edit { it[booleanPreferencesKey("storage.low.notification.enabled")] = true }
        val snapshot = AnalyzerSettingsBackupContributor(
            mockk<AnalyzerSettings>().apply { every { dataStore } returns source },
        ).snapshot()!!

        val target = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { tempDir.resolve("target.preferences_pb").toFile() },
        )
        target.edit { it[booleanPreferencesKey("storage.low.notification.armed")] = false }

        AnalyzerSettingsBackupContributor(
            mockk<AnalyzerSettings>().apply { every { dataStore } returns target },
        ).restore(snapshot, RestoreMode.REPLACE)

        val prefs = target.data.first()
        prefs[booleanPreferencesKey("storage.low.notification.armed")] shouldBe false
        prefs[booleanPreferencesKey("storage.low.notification.enabled")] shouldBe true
    }
}
