package eu.darken.sdmse.common.storage

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class StorageIdTest : BaseTest() {

    @Test
    fun `null fsUuid returns null`() {
        StorageId.parseVolumeUuid(null).shouldBeNull()
    }

    @Test
    fun `real UUID is parsed as-is`() {
        val real = "12345678-1234-1234-1234-123456789abc"
        StorageId.parseVolumeUuid(real)!!.toString() shouldBe real
    }

    @Test
    fun `FAT 4+4 hex label is FAT-prefixed`() {
        val u = StorageId.parseVolumeUuid("EFFD-F4D5")
        u shouldNotBe null
        u!!.toString().startsWith(StorageId.FAT_UUID_PREFIX) shouldBe true
        u.toString() shouldBe "${StorageId.FAT_UUID_PREFIX}effdf4d5"
    }

    @Test
    fun `exotic 16-hex fsUuid is synthesised deterministically (#2418)`() {
        val a = StorageId.parseVolumeUuid("1C32C2D032C2AE58")
        val b = StorageId.parseVolumeUuid("1C32C2D032C2AE58")
        a shouldNotBe null
        a shouldBe b
        // Must not be mis-tagged as FAT, otherwise the size-mismatch heuristic from #2389 fires.
        a!!.toString().startsWith(StorageId.FAT_UUID_PREFIX) shouldBe false
    }

    @Test
    fun `different exotic fsUuids produce different UUIDs`() {
        val a = StorageId.parseVolumeUuid("1C32C2D032C2AE58")
        val b = StorageId.parseVolumeUuid("ABCDEF0123456789")
        a shouldNotBe b
    }

    @Test
    fun `garbage fsUuid still produces a UUID rather than null`() {
        StorageId.parseVolumeUuid("not a uuid at all !!!") shouldNotBe null
    }
}
