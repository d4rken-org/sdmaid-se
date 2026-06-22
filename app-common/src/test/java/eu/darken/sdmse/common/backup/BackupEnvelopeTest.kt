package eu.darken.sdmse.common.backup

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class BackupEnvelopeTest : BaseTest() {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `envelope round-trips through json`() {
        val original = BackupEnvelope(
            createdAt = Instant.ofEpochMilli(1_700_000_000_000L),
            appVersionCode = 1234L,
            appVersionName = "1.2.3",
            flavor = "FOSS",
            androidSdkInt = 34,
            androidRelease = "14",
            deviceManufacturer = "Pixel",
            deviceModel = "7",
            sections = mapOf(
                "appcleaner" to buildJsonObject { put("filter.x", true) },
                "exclusions" to JsonPrimitive("{\"version\":1}"),
            ),
        )

        val raw = json.encodeToString(original)
        val restored = json.decodeFromString<BackupEnvelope>(raw)

        restored shouldBe original
        restored.version shouldBe BackupEnvelope.VERSION
        restored.createdAt shouldBe Instant.ofEpochMilli(1_700_000_000_000L)
        restored.sections.keys shouldBe setOf("appcleaner", "exclusions")
    }

    @Test
    fun `unknown extra fields are tolerated`() {
        val valid = BackupEnvelope(
            createdAt = Instant.ofEpochMilli(1_700_000_000_000L),
            appVersionCode = 1L,
            appVersionName = "1.0",
            flavor = "FOSS",
            androidSdkInt = 30,
            androidRelease = "11",
            deviceManufacturer = "x",
            deviceModel = "y",
            sections = emptyMap(),
        )
        // Re-encode with a junk field a future app version might add.
        val withExtra = buildJsonObject {
            json.parseToJsonElement(json.encodeToString(valid)).jsonObject.forEach { (k, v) -> put(k, v) }
            put("somethingFromTheFuture", true)
        }.toString()

        val restored = json.decodeFromString<BackupEnvelope>(withExtra)
        restored.flavor shouldBe "FOSS"
        restored.sections.isEmpty() shouldBe true
    }
}
