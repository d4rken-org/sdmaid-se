package eu.darken.sdmse.systemcleaner.core.filter.custom

import android.content.Context
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import eu.darken.sdmse.systemcleaner.core.rwDataStoreValue
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.UUID

class CustomFilterRepoTest : BaseTest() {

    private lateinit var tempDir: File

    @BeforeEach
    fun setupTemp() {
        tempDir = Files.createTempDirectory("systemcleaner-repo-test-").toFile()
    }

    @AfterEach
    fun cleanupTemp() {
        tempDir.deleteRecursively()
    }

    private fun config(
        id: String = UUID.randomUUID().toString(),
        label: String = "Test Filter",
    ): CustomFilterConfig = CustomFilterConfig(
        identifier = id,
        label = label,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        modifiedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    private class Harness(
        val repo: CustomFilterRepo,
        val settings: SystemCleanerSettings,
        val enabledValue: eu.darken.sdmse.common.datastore.DataStoreValue<Set<String>>,
        val legacy: LegacyFilterSupport,
        val filterDir: File,
    )

    private fun harness(
        contextFilesDir: File = tempDir,
        initialEnabled: Set<String> = emptySet(),
    ): Harness {
        val context = mockk<Context>().apply {
            every { filesDir } returns contextFilesDir
        }
        val enabledValue = rwDataStoreValue(initialEnabled)
        val settings = mockk<SystemCleanerSettings>(relaxed = true).apply {
            every { enabledCustomFilter } returns enabledValue
        }
        val legacy = mockk<LegacyFilterSupport>(relaxed = true)
        val repo = CustomFilterRepo(
            context = context,
            json = Json,
            settings = settings,
            legacyImporter = legacy,
        )
        // Realized filter directory:
        return Harness(repo, settings, enabledValue, legacy, File(contextFilesDir, "systemcleaner/customfilter2"))
    }

    @Test
    fun `configs flow emits empty list when no files exist`() = runTest2 {
        val h = harness()
        h.repo.configs.first() shouldBe emptyList()
    }

    @Test
    fun `save writes JSON file under filesDir customfilter2 directory`() = runTest2 {
        val h = harness()
        val cfg = config(id = "abc", label = "Saved one")

        h.repo.save(setOf(cfg))

        val onDisk = File(h.filterDir, "abc.json")
        onDisk.exists() shouldBe true
        onDisk.readText().contains("Saved one") shouldBe true
    }

    @Test
    fun `configs re-emits after save - new config appears`() = runTest2 {
        val h = harness()
        h.repo.configs.first() shouldBe emptyList()
        val cfg = config(id = "new")

        h.repo.save(setOf(cfg))

        val emitted = h.repo.configs.first().toList()
        emitted.map { it.identifier } shouldBe listOf("new")
    }

    @Test
    fun `remove deletes the file and clears settings entry`() = runTest2 {
        val h = harness()
        val cfg = config(id = "to-remove")
        h.repo.save(setOf(cfg))
        val onDisk = File(h.filterDir, "to-remove.json")
        onDisk.exists() shouldBe true

        h.repo.remove(setOf("to-remove"))

        onDisk.exists() shouldBe false
        // settings.clearCustomFilter triggers an update on enabledCustomFilter.
        io.mockk.coVerify(atLeast = 1) { h.enabledValue.update(any()) }
    }

    @Test
    fun `configs crashes on malformed JSON file - BUG-FIXME-6 documents current behavior`() = runTest2 {
        // BUG-FIXME-6: CustomFilterRepo.kt:61 calls `json.decodeFromString` without try/catch.
        // A single corrupt config file kills the entire `configs` flow until refresh() is
        // called again. This locks in the crash behavior — flip the test once decode is
        // wrapped in try/catch and corrupt files are silently skipped (or logged + skipped).
        val h = harness()
        // Plant a malformed JSON file directly in the filter directory.
        h.filterDir.mkdirs()
        File(h.filterDir, "corrupt.json").writeText("{ this is not valid json")

        shouldThrow<Exception> {
            h.repo.configs.first()
        }
    }

    @Test
    fun `configs throws NPE when filter directory cannot be created - BUG-FIXME-7 documents listFiles double-bang`() = runTest2 {
        // BUG-FIXME-7: CustomFilterRepo.kt:36-38 lazy-creates the filter directory via
        // mkdirs() but ignores the return value. Line 49 then calls `filterDir.listFiles()!!`
        // — if mkdirs() failed (because filesDir is a regular file, not a directory), the
        // !! throws NullPointerException when `configs` is collected.
        // Construct a filesDir that's actually a regular file so mkdirs cannot create a
        // subdirectory under it.
        val bogusFilesDir = File(tempDir, "files-as-file")
        bogusFilesDir.writeText("I am a file, not a directory")
        val h = harness(contextFilesDir = bogusFilesDir)

        shouldThrow<NullPointerException> {
            h.repo.configs.first()
        }
    }

    @Test
    fun `importFilter saves a config from modern JSON format`() = runTest2 {
        val h = harness()
        val cfg = config(id = "imported", label = "Imported filter")
        val json = Json.encodeToString(CustomFilterConfig.serializer(), cfg)

        h.repo.importFilter(listOf(RawFilter(name = "import.json", payload = json)))

        val emitted = h.repo.configs.first().toList()
        emitted.map { it.identifier } shouldBe listOf("imported")
    }

    @Test
    fun `importFilter falls back to legacy parser when modern decode fails`() = runTest2 {
        val h = harness()
        val legacyPayload = "<some legacy format>"
        val cfg = config(id = "legacy-id", label = "Legacy imported")
        coEvery { h.legacy.import(legacyPayload) } returns cfg

        h.repo.importFilter(listOf(RawFilter(name = "legacy.xml", payload = legacyPayload)))

        val emitted = h.repo.configs.first().toList()
        emitted.map { it.identifier } shouldBe listOf("legacy-id")
    }

    @Test
    fun `importFilter rethrows original error when both modern and legacy parsers fail`() = runTest2 {
        val h = harness()
        coEvery { h.legacy.import(any()) } returns null

        shouldThrow<Exception> {
            h.repo.importFilter(listOf(RawFilter(name = "bad.json", payload = "garbage")))
        }
    }

    @Test
    fun `exportFilters produces RawFilter with label and id-tail in name`() = runTest2 {
        val h = harness()
        val identifier = "0123456789abcdef-extra-tail"
        val cfg = config(id = identifier, label = "Exported")
        h.repo.save(setOf(cfg))

        val exported = h.repo.exportFilters(setOf(identifier))

        exported.size shouldBe 1
        val raw = exported.single()
        // RawFilter.name format: "<label> - <last 10 chars of id>.json"
        raw.name shouldBe "Exported - ${identifier.takeLast(10)}.json"
        raw.payload.contains("Exported") shouldBe true
    }

    @Test
    fun `generateIdentifier returns parseable UUID`() {
        val h = harness()
        val id = h.repo.generateIdentifier()

        // Should not throw on UUID parse.
        UUID.fromString(id)
    }

    @Test
    fun `configs clears orphan settings entries with no matching file on disk`() = runTest2 {
        // settings has IDs for filters that no longer have a config file on disk; loading
        // configs triggers cleanup of those orphan entries via clearCustomFilter.
        val h = harness(initialEnabled = setOf("orphan-id"))
        // No config file for "orphan-id" on disk.

        h.repo.configs.first()

        // settings.clearCustomFilter("orphan-id") was called, which updates enabledCustomFilter.
        io.mockk.coVerify(atLeast = 1) { h.enabledValue.update(any()) }
    }
}
