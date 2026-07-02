package eu.darken.sdmse.common.backup

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import eu.darken.sdmse.common.upgrade.UpgradeRepo
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
 * Regression guard for the on-disk backup format. The committed fixtures represent a frozen v1
 * archive (`manifest.json` + a `sections/<key>.json`); if this test starts failing, the format
 * changed — decide whether that's an intended, version-bumped change or an accidental break before
 * touching the fixtures.
 */
class GoldenBackupFormatTest : BaseTest() {

    private val json = Json { ignoreUnknownKeys = true }
    private val manager = ConfigBackupManager(
        context = mockk<Context>(relaxed = true),
        sectionContributors = emptySet(),
        databaseContributors = emptySet(),
        json = json,
        upgradeRepo = mockk<UpgradeRepo>(relaxed = true),
        envelopeSource = mockk<BackupEnvelopeSource>(relaxed = true),
        gate = BackupOperationGate(),
        limits = BackupLimits(),
    )

    private fun load(name: String): String = javaClass.classLoader!!
        .getResourceAsStream("backup/$name")!!
        .bufferedReader().use { it.readText() }

    private class TestSettingsContributor(
        dataStore: DataStore<Preferences>,
    ) : DataStoreSettingsBackupContributor(dataStore) {
        override val key = "general"
    }

    @Test
    fun `golden v1 manifest still parses`() {
        val envelope = manager.parse(load("golden-manifest-v1.json"))

        envelope.version shouldBe 1
        envelope.flavor shouldBe "GPLAY"
        envelope.appVersionName shouldBe "1.7.5"
        envelope.appVersionCode shouldBe 1750000L
        envelope.androidRelease shouldBe "14"
        envelope.deviceModel shouldBe "Pixel 8"
    }

    @Test
    fun `golden v1 settings section restores into a fresh store`(@TempDir tempDir: Path) = runTest {
        val element = json.parseToJsonElement(load("golden-section-general-v1.json"))
        val store = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { tempDir.resolve("golden.preferences_pb").toFile() },
        )

        TestSettingsContributor(store).restore(element, RestoreMode.REPLACE)

        val prefs = store.data.first()
        // Complex @Serializable settings are stored as their JSON string, so the value is quoted.
        prefs[stringPreferencesKey("core.ui.theme.mode")] shouldBe "\"DARK\""
        prefs[booleanPreferencesKey("core.ui.previews.enabled")] shouldBe true
        prefs[booleanPreferencesKey("dashboard.oneclick.onepass.enabled")] shouldBe false
    }
}
