package eu.darken.sdmse.common.backup

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class BackupEnvelopeTest : BaseTest() {

    private val json = Json { ignoreUnknownKeys = true }

    private fun sample() = BackupEnvelope(
        createdAt = Instant.ofEpochMilli(1_700_000_000_000L),
        appVersionCode = 1234L,
        appVersionName = "1.2.3",
        flavor = "FOSS",
        androidSdkInt = 34,
        androidRelease = "14",
        deviceManufacturer = "Pixel",
        deviceModel = "7",
    )

    @Test
    fun `manifest round-trips through json`() {
        val original = sample()
        val restored = json.decodeFromString<BackupEnvelope>(json.encodeToString(original))

        restored shouldBe original
        restored.version shouldBe BackupEnvelope.VERSION
        restored.createdAt shouldBe Instant.ofEpochMilli(1_700_000_000_000L)
    }

    @Test
    fun `unknown extra fields are tolerated`() {
        val withExtra = buildJsonObject {
            json.parseToJsonElement(json.encodeToString(sample())).jsonObject.forEach { (k, v) -> put(k, v) }
            put("somethingFromTheFuture", true)
        }.toString()

        json.decodeFromString<BackupEnvelope>(withExtra).flavor shouldBe "FOSS"
    }
}
