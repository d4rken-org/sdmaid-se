package eu.darken.sdmse.common.backup

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class BackupCodecTest : BaseTest() {

    @Test
    fun `gzip round-trip preserves content incl non-ascii`() {
        val json = """{"version":1,"note":"wörld 🚀 — café"}"""
        BackupCodec.decode(BackupCodec.encode(json)) shouldBe json
    }

    @Test
    fun `encoded output is gzip framed`() {
        val encoded = BackupCodec.encode("hello")
        encoded[0] shouldBe 0x1f.toByte()
        encoded[1] shouldBe 0x8b.toByte()
    }

    @Test
    fun `decode falls back to plain utf8 for uncompressed backups`() {
        // Older/plain .json backups (and the golden fixture) must still restore.
        val json = """{"version":1,"flavor":"FOSS"}"""
        BackupCodec.decode(json.toByteArray(Charsets.UTF_8)) shouldBe json
    }
}
