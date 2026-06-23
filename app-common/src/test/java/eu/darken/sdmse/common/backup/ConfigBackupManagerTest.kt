package eu.darken.sdmse.common.backup

import android.content.Context
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.UpgradeRequiredException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testhelpers.BaseTest
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Path
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ConfigBackupManagerTest : BaseTest() {

    private val json = Json { ignoreUnknownKeys = true }

    private class FakeInfo(override val isPro: Boolean) : UpgradeRepo.Info {
        override val type = UpgradeRepo.Type.FOSS
        override val upgradedAt: Instant? = null
        override val error: Throwable? = null
    }

    private class FakeUpgradeRepo(pro: Boolean) : UpgradeRepo {
        override val storeSite = ""
        override val upgradeSite = ""
        override val betaSite = ""
        override val upgradeInfo: Flow<UpgradeRepo.Info> = flowOf(FakeInfo(pro))
        override suspend fun refresh() {}
    }

    private class FakeContributor(
        override val key: String,
        override val restoreOrder: Int = ConfigBackupContributor.ORDER_SETTINGS,
        private val failRestore: Boolean = false,
        private val recorder: MutableList<String>? = null,
    ) : ConfigBackupContributor {
        var restoredWith: Pair<JsonElement, RestoreMode>? = null
        override suspend fun snapshot(): JsonElement = JsonPrimitive("snap-$key")
        override suspend fun restore(data: JsonElement, mode: RestoreMode) {
            if (failRestore) throw RuntimeException("boom")
            recorder?.add(key)
            restoredWith = data to mode
        }
    }

    private class FakeDbContributor(
        override val key: String,
    ) : DatabaseBackupContributor {
        var restoredBytes: ByteArray? = null
        var restoredMode: RestoreMode? = null
        override suspend fun exportTo(target: File) = target.writeBytes("db-$key".toByteArray())
        override suspend fun restoreFrom(source: File, mode: RestoreMode) {
            restoredBytes = source.readBytes()
            restoredMode = mode
        }
    }

    private fun manager(
        tmp: Path,
        sections: Set<ConfigBackupContributor> = emptySet(),
        databases: Set<DatabaseBackupContributor> = emptySet(),
        pro: Boolean = true,
    ): ConfigBackupManager {
        val ctx = mockk<Context>()
        every { ctx.cacheDir } returns tmp.toFile()
        return ConfigBackupManager(ctx, sections, databases, json, FakeUpgradeRepo(pro))
    }

    private fun envelope(sections: Map<String, JsonElement>, version: Int = BackupEnvelope.VERSION) = BackupEnvelope(
        version = version,
        createdAt = Instant.ofEpochMilli(1_700_000_000_000L),
        appVersionCode = 1L,
        appVersionName = "1.0",
        flavor = "FOSS",
        androidSdkInt = 30,
        androidRelease = "11",
        deviceManufacturer = "x",
        deviceModel = "y",
        sections = sections,
    )

    private fun writeZip(tmp: Path, configJson: String, dbEntries: Map<String, ByteArray> = emptyMap()): File {
        val zip = File.createTempFile("backup-", ".zip", tmp.toFile())
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("config.json"))
            out.write(configJson.toByteArray())
            out.closeEntry()
            dbEntries.forEach { (key, bytes) ->
                out.putNextEntry(ZipEntry("databases/$key"))
                out.write(bytes)
                out.closeEntry()
            }
        }
        return zip
    }

    @Test
    fun `writeBackup is denied for non-pro users`(@TempDir tmp: Path) = runTest {
        shouldThrow<UpgradeRequiredException> {
            manager(tmp, pro = false).writeBackup(ByteArrayOutputStream())
        }
    }

    @Test
    fun `parse rejects a newer format version`(@TempDir tmp: Path) = runTest {
        val raw = json.encodeToString(envelope(emptyMap(), version = BackupEnvelope.VERSION + 1))
        shouldThrow<UnsupportedBackupVersionException> { manager(tmp).parse(raw) }
    }

    @Test
    fun `parse rejects blank and malformed input`(@TempDir tmp: Path) = runTest {
        val mgr = manager(tmp)
        shouldThrow<InvalidBackupException> { mgr.parse("") }
        shouldThrow<InvalidBackupException> { mgr.parse("{not valid json") }
        shouldThrow<InvalidBackupException> { mgr.parse("[]") }
    }

    @Test
    fun `parse of a zip without config_json fails`(@TempDir tmp: Path) = runTest {
        val zip = File.createTempFile("empty-", ".zip", tmp.toFile())
        ZipOutputStream(zip.outputStream()).use { it.putNextEntry(ZipEntry("nope.txt")); it.closeEntry() }
        shouldThrow<InvalidBackupException> { manager(tmp).parse(zip) }
    }

    @Test
    fun `restore runs content before settings, skips missing, ignores unknown`(@TempDir tmp: Path) = runTest {
        val recorder = mutableListOf<String>()
        val content = FakeContributor("exclusions", ConfigBackupContributor.ORDER_CONTENT, recorder = recorder)
        val settings = FakeContributor("appcleaner", ConfigBackupContributor.ORDER_SETTINGS, recorder = recorder)
        val absent = FakeContributor("not-in-backup", recorder = recorder)

        val zip = writeZip(
            tmp,
            json.encodeToString(
                envelope(
                    mapOf(
                        "appcleaner" to JsonPrimitive("x"),
                        "exclusions" to JsonPrimitive("y"),
                        "unknown-section" to JsonPrimitive("z"),
                    ),
                ),
            ),
        )

        val result = manager(tmp, sections = setOf(settings, content, absent)).restore(zip, RestoreMode.MERGE)

        recorder shouldBe listOf("exclusions", "appcleaner")
        absent.restoredWith shouldBe null
        result.isCompleteSuccess shouldBe true
    }

    @Test
    fun `restore dispatches database entries to db contributors`(@TempDir tmp: Path) = runTest {
        val db = FakeDbContributor("stats.db")
        val payload = "sqlite-bytes".toByteArray()
        val zip = writeZip(tmp, json.encodeToString(envelope(emptyMap())), mapOf("stats.db" to payload))

        val result = manager(tmp, databases = setOf(db)).restore(zip, RestoreMode.REPLACE)

        db.restoredBytes!!.toList() shouldBe payload.toList()
        db.restoredMode shouldBe RestoreMode.REPLACE
        result.restored shouldBe setOf("stats.db")
    }

    @Test
    fun `restore isolates a failing section`(@TempDir tmp: Path) = runTest {
        val good = FakeContributor("good")
        val bad = FakeContributor("bad", failRestore = true)
        val zip = writeZip(
            tmp,
            json.encodeToString(envelope(mapOf("good" to JsonPrimitive("1"), "bad" to JsonPrimitive("2")))),
        )

        val result = manager(tmp, sections = setOf(good, bad)).restore(zip, RestoreMode.REPLACE)

        good.restoredWith?.second shouldBe RestoreMode.REPLACE
        result.isCompleteSuccess shouldBe false
        result.failures.map { it.key } shouldBe listOf("bad")
    }
}
