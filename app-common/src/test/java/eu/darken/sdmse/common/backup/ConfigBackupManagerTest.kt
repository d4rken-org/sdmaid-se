package eu.darken.sdmse.common.backup

import android.content.Context
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.UpgradeRequiredException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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
        override val isSettled: Flow<Boolean> = flowOf(true)
        override suspend fun refresh() {}
    }

    private class FakeContributor(
        override val key: String,
        override val restoreOrder: Int = ConfigBackupContributor.ORDER_SETTINGS,
        private val failRestore: Boolean = false,
        private val failSnapshot: Boolean = false,
        private val failValidate: Boolean = false,
        private val recorder: MutableList<String>? = null,
    ) : ConfigBackupContributor {
        var restoredWith: Pair<JsonElement, RestoreMode>? = null
        override suspend fun snapshot(): JsonElement {
            if (failSnapshot) throw RuntimeException("snap boom")
            return JsonPrimitive("snap-$key")
        }

        override suspend fun validate(data: JsonElement) {
            if (failValidate) throw RuntimeException("invalid section")
        }

        override suspend fun restore(data: JsonElement, mode: RestoreMode) {
            if (failRestore) throw RuntimeException("boom")
            recorder?.add(key)
            restoredWith = data to mode
        }
    }

    private class FakeDbContributor(
        override val key: String,
        private val failValidate: Boolean = false,
        private val schemaMismatch: Boolean = false,
        private val failRestore: Boolean = false,
    ) : DatabaseBackupContributor {
        var restoredBytes: ByteArray? = null
        var restoredMode: RestoreMode? = null
        override suspend fun exportTo(target: File) = target.writeBytes("db-$key".toByteArray())
        override suspend fun validate(source: File) {
            if (schemaMismatch) throw DatabaseSchemaMismatchException(key, "test mismatch")
            if (failValidate) throw RuntimeException("bad db")
        }

        override suspend fun restoreFrom(source: File, mode: RestoreMode) {
            if (failRestore) throw RuntimeException("db boom")
            restoredBytes = source.readBytes()
            restoredMode = mode
        }
    }

    private fun safetyDir(tmp: Path): File = tmp.resolve("files/backup").toFile()

    private fun safetySnapshots(tmp: Path): List<File> = safetyDir(tmp).listFiles().orEmpty()
        .filter { it.name.startsWith("pre-restore-") }

    private fun manager(
        tmp: Path,
        sections: Set<ConfigBackupContributor> = emptySet(),
        databases: Set<DatabaseBackupContributor> = emptySet(),
        pro: Boolean = true,
        limits: BackupLimits = BackupLimits(),
        gate: BackupOperationGate = BackupOperationGate(),
    ): ConfigBackupManager {
        val ctx = mockk<Context>()
        every { ctx.cacheDir } returns tmp.resolve("cache").toFile().apply { mkdirs() }
        every { ctx.noBackupFilesDir } returns tmp.resolve("files").toFile().apply { mkdirs() }
        val envelopeSource = mockk<BackupEnvelopeSource>().apply {
            every { create() } returns envelope()
        }
        return ConfigBackupManager(
            context = ctx,
            sectionContributors = sections,
            databaseContributors = databases,
            json = json,
            upgradeRepo = FakeUpgradeRepo(pro),
            envelopeSource = envelopeSource,
            gate = gate,
            limits = limits,
        )
    }

    private fun envelope(version: Int = BackupEnvelope.VERSION) = BackupEnvelope(
        version = version,
        createdAt = Instant.ofEpochMilli(1_700_000_000_000L),
        appVersionCode = 1L,
        appVersionName = "1.0",
        flavor = "FOSS",
        androidSdkInt = 30,
        androidRelease = "11",
        deviceManufacturer = "x",
        deviceModel = "y",
    )

    private fun writeZip(
        tmp: Path,
        manifest: String,
        sectionFiles: Map<String, String> = emptyMap(),
        dbEntries: Map<String, ByteArray> = emptyMap(),
    ): File {
        val zip = File.createTempFile("backup-", ".zip", tmp.toFile())
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("manifest.json")); out.write(manifest.toByteArray()); out.closeEntry()
            sectionFiles.forEach { (key, content) ->
                out.putNextEntry(ZipEntry("sections/$key.json")); out.write(content.toByteArray()); out.closeEntry()
            }
            dbEntries.forEach { (key, bytes) ->
                out.putNextEntry(ZipEntry("databases/$key")); out.write(bytes); out.closeEntry()
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
    fun `writeBackup produces an archive that parses back`(@TempDir tmp: Path) = runTest {
        val out = ByteArrayOutputStream()
        val result = manager(tmp, sections = setOf(FakeContributor("appcleaner"))).writeBackup(out)

        result.written shouldBe setOf("appcleaner")
        result.isCompleteSuccess shouldBe true

        val roundtrip = File.createTempFile("roundtrip-", ".zip", tmp.toFile())
        roundtrip.writeBytes(out.toByteArray())
        manager(tmp).parse(roundtrip).version shouldBe BackupEnvelope.VERSION
    }

    @Test
    fun `writeEntries records written sections and databases`(@TempDir tmp: Path) = runTest {
        val section = FakeContributor("appcleaner")
        val db = FakeDbContributor("stats.db")
        val mgr = manager(tmp, sections = setOf(section), databases = setOf(db))

        val result = ZipOutputStream(ByteArrayOutputStream()).use { mgr.writeEntries(it) }

        result.written shouldBe setOf("appcleaner", "stats.db")
        result.isCompleteSuccess shouldBe true
    }

    @Test
    fun `writeEntries isolates a failing snapshot and reports it`(@TempDir tmp: Path) = runTest {
        val good = FakeContributor("good")
        val bad = FakeContributor("bad", failSnapshot = true)
        val mgr = manager(tmp, sections = setOf(good, bad))

        val result = ZipOutputStream(ByteArrayOutputStream()).use { mgr.writeEntries(it) }

        result.written shouldBe setOf("good")
        result.isCompleteSuccess shouldBe false
        result.failures.map { it.key } shouldBe listOf("bad")
    }

    @Test
    fun `parse rejects a newer format version`(@TempDir tmp: Path) = runTest {
        val raw = json.encodeToString(envelope(version = BackupEnvelope.VERSION + 1))
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
    fun `parse of a zip without manifest fails`(@TempDir tmp: Path) = runTest {
        val zip = File.createTempFile("empty-", ".zip", tmp.toFile())
        ZipOutputStream(zip.outputStream()).use { it.putNextEntry(ZipEntry("nope.txt")); it.closeEntry() }
        shouldThrow<InvalidBackupException> { manager(tmp).parse(zip) }
    }

    @Test
    fun `parse rejects an oversized manifest before reading it into memory`(@TempDir tmp: Path) = runTest {
        // parse() runs at import time, BEFORE the integrity pass — it must cap reads on its own.
        val zip = writeZip(tmp, manifest = "x".repeat(20_000))

        shouldThrow<InvalidBackupException> {
            manager(tmp, limits = BackupLimits(maxTextEntryBytes = 10_000)).parse(zip)
        }
    }

    @Test
    fun `parse rejects a non-zip file`(@TempDir tmp: Path) = runTest {
        val notZip = File.createTempFile("notzip-", ".zip", tmp.toFile())
        notZip.writeText("definitely not a zip archive")
        shouldThrow<InvalidBackupException> { manager(tmp).parse(notZip) }
    }

    @Test
    fun `restore runs content before settings, skips missing, ignores unknown`(@TempDir tmp: Path) = runTest {
        val recorder = mutableListOf<String>()
        val content = FakeContributor("exclusions", ConfigBackupContributor.ORDER_CONTENT, recorder = recorder)
        val settings = FakeContributor("appcleaner", ConfigBackupContributor.ORDER_SETTINGS, recorder = recorder)
        val absent = FakeContributor("not-in-backup", recorder = recorder)

        val zip = writeZip(
            tmp,
            manifest = json.encodeToString(envelope()),
            sectionFiles = mapOf(
                "appcleaner" to "\"x\"",
                "exclusions" to "\"y\"",
                "unknown-section" to "\"z\"",
            ),
        )

        val restored = manager(tmp, sections = setOf(settings, content, absent)).restore(zip, RestoreMode.MERGE)

        recorder shouldBe listOf("exclusions", "appcleaner")
        absent.restoredWith shouldBe null
        restored shouldBe setOf("exclusions", "appcleaner")
    }

    @Test
    fun `restore dispatches database entries to db contributors`(@TempDir tmp: Path) = runTest {
        val db = FakeDbContributor("stats.db")
        val payload = "sqlite-bytes".toByteArray()
        val zip = writeZip(tmp, json.encodeToString(envelope()), dbEntries = mapOf("stats.db" to payload))

        val restored = manager(tmp, databases = setOf(db)).restore(zip, RestoreMode.REPLACE)

        db.restoredBytes!!.toList() shouldBe payload.toList()
        db.restoredMode shouldBe RestoreMode.REPLACE
        restored shouldBe setOf("stats.db")
    }

    @Test
    fun `restore rejects a corrupt archive before applying anything`(@TempDir tmp: Path) = runTest {
        val recorder = mutableListOf<String>()
        val good = FakeContributor("good", recorder = recorder)
        val zip = writeZip(tmp, json.encodeToString(envelope()), sectionFiles = mapOf("good" to "\"x\""))
        // Truncate the archive (drops the end-of-central-directory record) to corrupt it.
        val bytes = zip.readBytes()
        zip.writeBytes(bytes.copyOf(bytes.size - 40))

        shouldThrow<InvalidBackupException> {
            manager(tmp, sections = setOf(good)).restore(zip, RestoreMode.REPLACE)
        }
        recorder shouldBe emptyList<String>()
    }

    @Test
    fun `preflight rejects an invalid section before anything is applied`(@TempDir tmp: Path) = runTest {
        val recorder = mutableListOf<String>()
        val good = FakeContributor("good", restoreOrder = 0, recorder = recorder)
        val bad = FakeContributor("bad", restoreOrder = 1, failValidate = true, recorder = recorder)
        val zip = writeZip(
            tmp,
            manifest = json.encodeToString(envelope()),
            sectionFiles = mapOf("good" to "\"1\"", "bad" to "\"2\""),
        )

        shouldThrow<InvalidBackupException> {
            manager(tmp, sections = setOf(good, bad)).restore(zip, RestoreMode.REPLACE)
        }
        recorder shouldBe emptyList<String>()
        safetySnapshots(tmp).shouldBeEmpty()
    }

    @Test
    fun `preflight rejects an incompatible database before anything is applied`(@TempDir tmp: Path) = runTest {
        val recorder = mutableListOf<String>()
        val section = FakeContributor("good", recorder = recorder)
        val db = FakeDbContributor("stats.db", failValidate = true)
        val zip = writeZip(
            tmp,
            manifest = json.encodeToString(envelope()),
            sectionFiles = mapOf("good" to "\"1\""),
            dbEntries = mapOf("stats.db" to "sqlite-bytes".toByteArray()),
        )

        shouldThrow<InvalidBackupException> {
            manager(tmp, sections = setOf(section), databases = setOf(db)).restore(zip, RestoreMode.REPLACE)
        }
        recorder shouldBe emptyList<String>()
        safetySnapshots(tmp).shouldBeEmpty()
    }

    @Test
    fun `inspect reports schema-incompatible databases without applying anything`(@TempDir tmp: Path) = runTest {
        val mismatched = FakeDbContributor("stats.db", schemaMismatch = true)
        val fine = FakeDbContributor("swiper.db")
        val zip = writeZip(
            tmp,
            manifest = json.encodeToString(envelope()),
            dbEntries = mapOf("stats.db" to "a".toByteArray(), "swiper.db" to "b".toByteArray()),
        )

        val inspection = manager(tmp, databases = setOf(mismatched, fine)).inspect(zip)

        inspection.envelope.version shouldBe BackupEnvelope.VERSION
        inspection.incompatibleDatabases shouldBe setOf("stats.db")
        mismatched.restoredBytes shouldBe null
        fine.restoredBytes shouldBe null
    }

    @Test
    fun `restore skips consented incompatible databases and applies everything else`(@TempDir tmp: Path) = runTest {
        val recorder = mutableListOf<String>()
        val section = FakeContributor("good", recorder = recorder)
        val mismatched = FakeDbContributor("stats.db", schemaMismatch = true)
        val fine = FakeDbContributor("swiper.db")
        val zip = writeZip(
            tmp,
            manifest = json.encodeToString(envelope()),
            sectionFiles = mapOf("good" to "\"x\""),
            dbEntries = mapOf("stats.db" to "a".toByteArray(), "swiper.db" to "b".toByteArray()),
        )

        val restored = manager(tmp, sections = setOf(section), databases = setOf(mismatched, fine))
            .restore(zip, RestoreMode.REPLACE, acceptedIncompatible = setOf("stats.db"))

        restored shouldBe setOf("good", "swiper.db")
        recorder shouldBe listOf("good")
        mismatched.restoredBytes shouldBe null
        fine.restoredBytes!!.toString(Charsets.UTF_8) shouldBe "b"
    }

    @Test
    fun `restore still rejects an incompatible database without consent`(@TempDir tmp: Path) = runTest {
        val recorder = mutableListOf<String>()
        val section = FakeContributor("good", recorder = recorder)
        val mismatched = FakeDbContributor("stats.db", schemaMismatch = true)
        val zip = writeZip(
            tmp,
            manifest = json.encodeToString(envelope()),
            sectionFiles = mapOf("good" to "\"x\""),
            dbEntries = mapOf("stats.db" to "a".toByteArray()),
        )

        shouldThrow<DatabaseSchemaMismatchException> {
            manager(tmp, sections = setOf(section), databases = setOf(mismatched))
                .restore(zip, RestoreMode.REPLACE, acceptedIncompatible = setOf("some-other.db"))
        }
        recorder shouldBe emptyList<String>()
    }

    @Test
    fun `replace aborts on the first failure and keeps the safety snapshot`(@TempDir tmp: Path) = runTest {
        val recorder = mutableListOf<String>()
        val first = FakeContributor("aaa", restoreOrder = 0, recorder = recorder)
        val bad = FakeContributor("bbb", restoreOrder = 1, failRestore = true, recorder = recorder)
        val never = FakeContributor("ccc", restoreOrder = 2, recorder = recorder)
        val zip = writeZip(
            tmp,
            manifest = json.encodeToString(envelope()),
            sectionFiles = mapOf("aaa" to "\"1\"", "bbb" to "\"2\"", "ccc" to "\"3\""),
        )

        val e = shouldThrow<RestoreFailedException> {
            manager(tmp, sections = setOf(first, bad, never)).restore(zip, RestoreMode.REPLACE)
        }

        recorder shouldBe listOf("aaa")
        never.restoredWith shouldBe null
        e.failedSections shouldBe listOf("bbb")
        e.recoveryBackup!!.exists() shouldBe true
    }

    @Test
    fun `replace aborts when a database restore fails`(@TempDir tmp: Path) = runTest {
        val db = FakeDbContributor("stats.db", failRestore = true)
        val zip = writeZip(tmp, json.encodeToString(envelope()), dbEntries = mapOf("stats.db" to "x".toByteArray()))

        val e = shouldThrow<RestoreFailedException> {
            manager(tmp, databases = setOf(db)).restore(zip, RestoreMode.REPLACE)
        }

        e.failedSections shouldBe listOf("stats.db")
        e.recoveryBackup!!.exists() shouldBe true
    }

    @Test
    fun `merge applies everything and then fails if any section failed`(@TempDir tmp: Path) = runTest {
        val recorder = mutableListOf<String>()
        val first = FakeContributor("aaa", restoreOrder = 0, recorder = recorder)
        val bad = FakeContributor("bbb", restoreOrder = 1, failRestore = true, recorder = recorder)
        val last = FakeContributor("ccc", restoreOrder = 2, recorder = recorder)
        val zip = writeZip(
            tmp,
            manifest = json.encodeToString(envelope()),
            sectionFiles = mapOf("aaa" to "\"1\"", "bbb" to "\"2\"", "ccc" to "\"3\""),
        )

        val e = shouldThrow<RestoreFailedException> {
            manager(tmp, sections = setOf(first, bad, last)).restore(zip, RestoreMode.MERGE)
        }

        recorder shouldBe listOf("aaa", "ccc")
        e.failedSections shouldBe listOf("bbb")
        e.recoveryBackup!!.exists() shouldBe true
    }

    @Test
    fun `restore deletes the safety snapshot on success`(@TempDir tmp: Path) = runTest {
        val good = FakeContributor("good")
        val zip = writeZip(tmp, json.encodeToString(envelope()), sectionFiles = mapOf("good" to "\"x\""))

        manager(tmp, sections = setOf(good)).restore(zip, RestoreMode.REPLACE)

        safetySnapshots(tmp).shouldBeEmpty()
    }

    @Test
    fun `restore is refused when the safety snapshot would be incomplete`(@TempDir tmp: Path) = runTest {
        val recorder = mutableListOf<String>()
        val good = FakeContributor("good", recorder = recorder)
        val brokenSnap = FakeContributor("brokensnap", failSnapshot = true)
        val zip = writeZip(tmp, json.encodeToString(envelope()), sectionFiles = mapOf("good" to "\"x\""))

        shouldThrow<SafetyBackupFailedException> {
            manager(tmp, sections = setOf(good, brokenSnap)).restore(zip, RestoreMode.REPLACE)
        }
        recorder shouldBe emptyList<String>()
        safetySnapshots(tmp).shouldBeEmpty()
    }

    @Test
    fun `restore purges stale safety snapshots only after the new snapshot is complete`(@TempDir tmp: Path) = runTest {
        val good = FakeContributor("good")
        val dir = safetyDir(tmp).apply { mkdirs() }
        val stale = File(dir, "pre-restore-111.zip").apply { writeText("stale leftover") }
        val zip = writeZip(tmp, json.encodeToString(envelope()), sectionFiles = mapOf("good" to "\"x\""))

        manager(tmp, sections = setOf(good)).restore(zip, RestoreMode.REPLACE)

        // Stale snapshot purged (new one was written), new one deleted on success.
        safetySnapshots(tmp).shouldBeEmpty()
        stale.exists() shouldBe false
    }

    @Test
    fun `a failed safety snapshot write never destroys an existing snapshot`(@TempDir tmp: Path) = runTest {
        val good = FakeContributor("good")
        val brokenSnap = FakeContributor("brokensnap", failSnapshot = true)
        val dir = safetyDir(tmp).apply { mkdirs() }
        val existing = File(dir, "pre-restore-111.zip").apply { writeText("previous recovery") }
        val zip = writeZip(tmp, json.encodeToString(envelope()), sectionFiles = mapOf("good" to "\"x\""))

        shouldThrow<SafetyBackupFailedException> {
            manager(tmp, sections = setOf(good, brokenSnap)).restore(zip, RestoreMode.REPLACE)
        }

        existing.exists() shouldBe true
    }

    @Test
    fun `restoring from a safety snapshot consumes it on success`(@TempDir tmp: Path) = runTest {
        val good = FakeContributor("good")
        val dir = safetyDir(tmp).apply { mkdirs() }
        val source = File(dir, "pre-restore-1.zip")
        writeZip(tmp, json.encodeToString(envelope()), sectionFiles = mapOf("good" to "\"x\""))
            .copyTo(source, overwrite = true)

        manager(tmp, sections = setOf(good)).restore(source, RestoreMode.REPLACE)

        source.exists() shouldBe false
    }

    @Test
    fun `merge is rejected for a safety snapshot source`(@TempDir tmp: Path) = runTest {
        val good = FakeContributor("good")
        val dir = safetyDir(tmp).apply { mkdirs() }
        val source = File(dir, "pre-restore-1.zip")
        writeZip(tmp, json.encodeToString(envelope()), sectionFiles = mapOf("good" to "\"x\""))
            .copyTo(source, overwrite = true)

        shouldThrow<IllegalArgumentException> {
            manager(tmp, sections = setOf(good)).restore(source, RestoreMode.MERGE)
        }
        // MERGE would consume the snapshot without faithfully recovering — nothing was touched.
        good.restoredWith shouldBe null
        source.exists() shouldBe true
    }

    @Test
    fun `restoring from a safety snapshot keeps it as recovery on failure without re-snapshotting`(
        @TempDir tmp: Path,
    ) = runTest {
        val bad = FakeContributor("bad", failRestore = true)
        val dir = safetyDir(tmp).apply { mkdirs() }
        val source = File(dir, "pre-restore-1.zip")
        writeZip(tmp, json.encodeToString(envelope()), sectionFiles = mapOf("bad" to "\"x\""))
            .copyTo(source, overwrite = true)

        val e = shouldThrow<RestoreFailedException> {
            manager(tmp, sections = setOf(bad)).restore(source, RestoreMode.REPLACE)
        }

        // The source IS the recovery — no snapshot-of-the-broken-state was created next to it.
        e.recoveryBackup shouldBe source
        source.exists() shouldBe true
        safetySnapshots(tmp) shouldBe listOf(source)
    }

    @Test
    fun `restore rejects an archive with too many entries`(@TempDir tmp: Path) = runTest {
        val zip = writeZip(
            tmp,
            manifest = json.encodeToString(envelope()),
            sectionFiles = mapOf("a" to "\"1\"", "b" to "\"2\"", "c" to "\"3\""),
        )

        shouldThrow<InvalidBackupException> {
            manager(tmp, limits = BackupLimits(maxEntries = 2)).restore(zip, RestoreMode.MERGE)
        }
    }

    @Test
    fun `restore rejects an oversized text entry`(@TempDir tmp: Path) = runTest {
        val zip = writeZip(
            tmp,
            manifest = json.encodeToString(envelope()),
            sectionFiles = mapOf("big" to "\"${"x".repeat(20_000)}\""),
        )

        shouldThrow<InvalidBackupException> {
            manager(tmp, limits = BackupLimits(maxTextEntryBytes = 10_000)).restore(zip, RestoreMode.MERGE)
        }
    }

    @Test
    fun `restore rejects an oversized database entry`(@TempDir tmp: Path) = runTest {
        val zip = writeZip(
            tmp,
            manifest = json.encodeToString(envelope()),
            dbEntries = mapOf("stats.db" to ByteArray(20_000)),
        )

        shouldThrow<InvalidBackupException> {
            manager(tmp, limits = BackupLimits(maxEntryBytes = 10_000)).restore(zip, RestoreMode.MERGE)
        }
    }

    @Test
    fun `restore is refused while tool work holds the gate`(@TempDir tmp: Path) = runTest {
        val gate = BackupOperationGate()
        val good = FakeContributor("good")
        val zip = writeZip(tmp, json.encodeToString(envelope()), sectionFiles = mapOf("good" to "\"x\""))
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val worker = launch { gate.runShared { entered.complete(Unit); release.await() } }
        entered.await()

        shouldThrow<BackupBusyException> {
            manager(tmp, sections = setOf(good), gate = gate).restore(zip, RestoreMode.MERGE)
        }
        good.restoredWith shouldBe null

        release.complete(Unit)
        worker.join()
    }
}
