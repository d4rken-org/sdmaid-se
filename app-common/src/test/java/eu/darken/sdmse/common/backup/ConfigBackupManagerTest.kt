package eu.darken.sdmse.common.backup

import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.UpgradeRequiredException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

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
        private val snap: JsonElement? = JsonPrimitive("snap-of-key"),
        private val failRestore: Boolean = false,
        private val recorder: MutableList<String>? = null,
    ) : ConfigBackupContributor {
        var restoredWith: Pair<JsonElement, RestoreMode>? = null
        override suspend fun snapshot(): JsonElement? = snap
        override suspend fun restore(data: JsonElement, mode: RestoreMode) {
            if (failRestore) throw RuntimeException("boom")
            recorder?.add(key)
            restoredWith = data to mode
        }
    }

    private fun manager(
        contributors: Set<ConfigBackupContributor> = emptySet(),
        pro: Boolean = true,
    ) = ConfigBackupManager(contributors, json, FakeUpgradeRepo(pro))

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

    @Test
    fun `createBackup is denied for non-pro users`() = runTest {
        shouldThrow<UpgradeRequiredException> {
            manager(pro = false).createBackup()
        }
    }

    @Test
    fun `parse rejects a newer format version`() = runTest {
        val raw = json.encodeToString(envelope(emptyMap(), version = BackupEnvelope.VERSION + 1))
        shouldThrow<UnsupportedBackupVersionException> { manager().parse(raw) }
    }

    @Test
    fun `parse rejects blank and malformed input`() = runTest {
        val mgr = manager()
        shouldThrow<InvalidBackupException> { mgr.parse("") }
        shouldThrow<InvalidBackupException> { mgr.parse("{not valid json") }
        shouldThrow<InvalidBackupException> { mgr.parse("[]") }
    }

    @Test
    fun `restore runs content before settings, skips missing, ignores unknown`() = runTest {
        val recorder = mutableListOf<String>()
        val content = FakeContributor("exclusions", ConfigBackupContributor.ORDER_CONTENT, recorder = recorder)
        val settings = FakeContributor("appcleaner", ConfigBackupContributor.ORDER_SETTINGS, recorder = recorder)
        val absent = FakeContributor("not-in-backup", recorder = recorder)

        val env = envelope(
            mapOf(
                "appcleaner" to JsonPrimitive("x"),
                "exclusions" to JsonPrimitive("y"),
                "unknown-section" to JsonPrimitive("z"),
            ),
        )

        val result = manager(setOf(settings, content, absent)).restore(env, RestoreMode.MERGE)

        recorder shouldBe listOf("exclusions", "appcleaner")
        absent.restoredWith shouldBe null
        result.isCompleteSuccess shouldBe true
        result.restored shouldBe setOf("appcleaner", "exclusions", "unknown-section")
    }

    @Test
    fun `restore isolates a failing section`() = runTest {
        val good = FakeContributor("good")
        val bad = FakeContributor("bad", failRestore = true)

        val env = envelope(mapOf("good" to JsonPrimitive("1"), "bad" to JsonPrimitive("2")))
        val result = manager(setOf(good, bad)).restore(env, RestoreMode.REPLACE)

        good.restoredWith?.second shouldBe RestoreMode.REPLACE
        result.isCompleteSuccess shouldBe false
        result.failures.map { it.key } shouldBe listOf("bad")
    }
}
