package eu.darken.sdmse.common.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testhelpers.BaseTest
import java.nio.file.Path

class DataStoreSettingsBackupContributorTest : BaseTest() {

    private class TestContributor(
        dataStore: DataStore<Preferences>,
        override val excludedKeys: Set<String> = emptySet(),
    ) : DataStoreSettingsBackupContributor(dataStore) {
        override val key = "test"
    }

    private fun store(tempDir: Path, name: String, scope: kotlinx.coroutines.CoroutineScope) =
        PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { tempDir.resolve("$name.preferences_pb").toFile() },
        )

    @Test
    fun `snapshot captures all supported types and excludes denylisted keys`(@TempDir tempDir: Path) = runTest {
        val source = store(tempDir, "source", this)
        source.edit {
            it[booleanPreferencesKey("b")] = true
            it[intPreferencesKey("i")] = 42
            it[longPreferencesKey("l")] = 99L
            it[floatPreferencesKey("f")] = 1.5f
            it[stringPreferencesKey("s")] = "hi"
            it[stringPreferencesKey("secret")] = "nope"
        }

        val snap = TestContributor(source, excludedKeys = setOf("secret")).snapshot()!!
        snap.jsonObject.keys shouldBe setOf("b", "i", "l", "f", "s")
    }

    @Test
    fun `snapshot returns null for an empty store`(@TempDir tempDir: Path) = runTest {
        val source = store(tempDir, "empty", this)
        TestContributor(source).snapshot() shouldBe null
    }

    @Test
    fun `merge restore applies backup values and keeps existing unrelated keys`(@TempDir tempDir: Path) = runTest {
        val source = store(tempDir, "source", this)
        source.edit {
            it[booleanPreferencesKey("b")] = true
            it[intPreferencesKey("i")] = 42
            it[longPreferencesKey("l")] = 99L
            it[floatPreferencesKey("f")] = 1.5f
            it[stringPreferencesKey("s")] = "hi"
        }
        val snap: JsonElement = TestContributor(source).snapshot()!!

        val target = store(tempDir, "target", this)
        target.edit { it[stringPreferencesKey("preexisting")] = "keep" }

        TestContributor(target).restore(snap, RestoreMode.MERGE)

        val prefs = target.data.first()
        prefs[booleanPreferencesKey("b")] shouldBe true
        prefs[intPreferencesKey("i")] shouldBe 42
        prefs[longPreferencesKey("l")] shouldBe 99L
        prefs[floatPreferencesKey("f")] shouldBe 1.5f
        prefs[stringPreferencesKey("s")] shouldBe "hi"
        prefs[stringPreferencesKey("preexisting")] shouldBe "keep"
    }

    @Test
    fun `replace restore wipes managed keys but preserves excluded keys`(@TempDir tempDir: Path) = runTest {
        val source = store(tempDir, "source", this)
        source.edit { it[stringPreferencesKey("s")] = "fromBackup" }
        val snap = TestContributor(source).snapshot()!!

        val target = store(tempDir, "target", this)
        target.edit {
            it[stringPreferencesKey("stale")] = "should-be-removed"
            it[stringPreferencesKey("secret")] = "device-local"
        }

        TestContributor(target, excludedKeys = setOf("secret")).restore(snap, RestoreMode.REPLACE)

        val prefs = target.data.first()
        prefs[stringPreferencesKey("s")] shouldBe "fromBackup"
        prefs[stringPreferencesKey("stale")] shouldBe null
        prefs[stringPreferencesKey("secret")] shouldBe "device-local"
    }
}
