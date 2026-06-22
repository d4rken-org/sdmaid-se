package eu.darken.sdmse.common.backup

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testhelpers.BaseTest
import java.nio.file.Path

/**
 * Regression guard for the on-disk backup format. The committed fixture represents a frozen v1
 * backup; if this test starts failing, the format changed — decide whether that's an intended,
 * version-bumped change or an accidental break before touching the fixture.
 */
class GoldenBackupFormatTest : BaseTest() {

    private val json = Json { ignoreUnknownKeys = true }
    private val manager = ConfigBackupManager(emptySet(), json, mockk<UpgradeRepo>(relaxed = true))

    private fun loadGolden(): String = javaClass.classLoader!!
        .getResourceAsStream("backup/golden-backup-v1.json")!!
        .bufferedReader().use { it.readText() }

    private class TestSettingsContributor(
        dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>,
    ) : DataStoreSettingsBackupContributor(dataStore) {
        override val key = "general"
    }

    @Test
    fun `golden v1 backup still parses`() {
        val envelope = manager.parse(loadGolden())

        envelope.version shouldBe 1
        envelope.flavor shouldBe "GPLAY"
        envelope.appVersionName shouldBe "1.7.5"
        envelope.appVersionCode shouldBe 1750000L
        envelope.androidRelease shouldBe "14"
        envelope.deviceModel shouldBe "Pixel 8"
        envelope.sections.keys shouldContainAll setOf("general", "appcleaner", "exclusions", "stats.db")
    }

    @Test
    fun `golden datastore section restores into a fresh store`(@TempDir tempDir: Path) = runTest {
        val envelope = manager.parse(loadGolden())
        val store = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { tempDir.resolve("golden.preferences_pb").toFile() },
        )

        TestSettingsContributor(store).restore(envelope.sections.getValue("general"), RestoreMode.REPLACE)

        val prefs = store.data.first()
        // Complex @Serializable settings are stored as their JSON string, so the value is quoted.
        prefs[stringPreferencesKey("core.ui.theme.mode")] shouldBe "\"DARK\""
        prefs[booleanPreferencesKey("core.ui.previews.enabled")] shouldBe true
        prefs[booleanPreferencesKey("dashboard.oneclick.onepass.enabled")] shouldBe false
    }
}
