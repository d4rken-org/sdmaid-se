package eu.darken.sdmse.common.backup

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testhelpers.BaseTest
import java.io.File
import java.nio.file.Path
import java.time.Instant

/**
 * Regression guard for the on-disk backup format. The committed fixtures represent a frozen v1
 * archive (`manifest.json` + `sections/<key>.json` + `databases/<key>`, plus the standalone
 * per-file fixtures); if this test starts failing, the format changed — decide whether that's an
 * intended, version-bumped change or an accidental break before touching the fixtures.
 */
class GoldenBackupFormatTest : BaseTest() {

    private val json = Json { ignoreUnknownKeys = true }

    private fun load(name: String): String = javaClass.classLoader!!
        .getResourceAsStream("backup/$name")!!
        .bufferedReader().use { it.readText() }

    private fun loadFile(name: String, target: File): File {
        javaClass.classLoader!!.getResourceAsStream("backup/$name")!!.use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
        return target
    }

    private class TestSettingsContributor(
        dataStore: DataStore<Preferences>,
    ) : DataStoreSettingsBackupContributor(dataStore) {
        override val key = "general"
    }

    private class RecordingDbContributor : DatabaseBackupContributor {
        override val key = "test.db"
        var restoredBytes: ByteArray? = null
        var restoredMode: RestoreMode? = null
        override suspend fun exportTo(target: File) = target.writeBytes(ByteArray(1))
        override suspend fun validate(source: File) = Unit
        override suspend fun restoreFrom(source: File, mode: RestoreMode) {
            restoredBytes = source.readBytes()
            restoredMode = mode
        }
    }

    private fun manager(
        tmp: Path,
        sections: Set<ConfigBackupContributor> = emptySet(),
        databases: Set<DatabaseBackupContributor> = emptySet(),
    ): ConfigBackupManager {
        val ctx = mockk<Context>()
        every { ctx.cacheDir } returns tmp.resolve("cache").toFile().apply { mkdirs() }
        every { ctx.noBackupFilesDir } returns tmp.resolve("files").toFile().apply { mkdirs() }
        val envelopeSource = mockk<BackupEnvelopeSource>().apply {
            every { create() } returns BackupEnvelope(
                createdAt = Instant.ofEpochMilli(1_700_000_000_000L),
                appVersionCode = 1L,
                appVersionName = "1.0",
                flavor = "FOSS",
                androidSdkInt = 34,
                androidRelease = "14",
                deviceManufacturer = "x",
                deviceModel = "y",
            )
        }
        return ConfigBackupManager(
            context = ctx,
            sectionContributors = sections,
            databaseContributors = databases,
            json = json,
            upgradeRepo = mockk<UpgradeRepo>(relaxed = true),
            envelopeSource = envelopeSource,
            gate = BackupOperationGate(),
            limits = BackupLimits(),
        )
    }

    private fun store(tempDir: Path, scope: CoroutineScope) = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { tempDir.resolve("golden.preferences_pb").toFile() },
    )

    @Test
    fun `golden v1 manifest still parses`(@TempDir tempDir: Path) {
        val envelope = manager(tempDir).parse(load("golden-manifest-v1.json"))

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
        val store = store(tempDir, this)

        TestSettingsContributor(store).restore(element, RestoreMode.REPLACE)

        val prefs = store.data.first()
        // Complex @Serializable settings are stored as their JSON string, so the value is quoted.
        prefs[stringPreferencesKey("core.ui.theme.mode")] shouldBe "\"DARK\""
        prefs[booleanPreferencesKey("core.ui.previews.enabled")] shouldBe true
        prefs[booleanPreferencesKey("dashboard.oneclick.onepass.enabled")] shouldBe false
    }

    @Test
    fun `golden v1 archive restores end-to-end through the manager`(@TempDir tempDir: Path) = runTest {
        // Locks the whole on-disk contract: zip layout, entry naming (sections/<key>.json,
        // databases/<key>), manifest gate, integrity pass, settings tag format, unknown-section
        // tolerance, and database dispatch. If this breaks, existing users' backups break.
        val zip = loadFile("golden-backup-v1.zip", tempDir.resolve("golden.zip").toFile())
        val store = store(tempDir, this)
        val settings = TestSettingsContributor(store)
        val db = RecordingDbContributor()
        val mgr = manager(tempDir, sections = setOf(settings), databases = setOf(db))

        val inspection = mgr.inspect(zip)
        inspection.envelope.version shouldBe 1
        inspection.incompatibleDatabases shouldBe emptySet<String>()

        val restored = mgr.restore(zip, RestoreMode.REPLACE)

        restored shouldBe setOf("general", "test.db")
        val prefs = store.data.first()
        prefs[stringPreferencesKey("core.ui.theme.mode")] shouldBe "\"DARK\""
        prefs[booleanPreferencesKey("core.ui.previews.enabled")] shouldBe true
        prefs[booleanPreferencesKey("dashboard.oneclick.onepass.enabled")] shouldBe false
        db.restoredBytes!!.toString(Charsets.UTF_8) shouldBe "golden-db-bytes-v1"
        db.restoredMode shouldBe RestoreMode.REPLACE
    }
}
