package eu.darken.sdmse.stats.core

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class LowStorageTest : BaseTest() {

    @Test
    fun `automatic caps at 2 GiB on a large volume`() {
        // 5% of 512 GB would be 25.6 GB, which is far more headroom than "running out".
        LowStorage.resolveThreshold(
            capacityBytes = 512_000_000_000L,
            customThresholdBytes = null,
        ) shouldBe LowStorage.AUTO_MAX_BYTES
    }

    @Test
    fun `automatic yields 5 percent on a small volume`() {
        LowStorage.resolveThreshold(
            capacityBytes = 8_000_000_000L,
            customThresholdBytes = null,
        ) shouldBe 400_000_000L
    }

    @Test
    fun `automatic on a zero capacity yields zero`() {
        LowStorage.resolveThreshold(
            capacityBytes = 0L,
            customThresholdBytes = null,
        ) shouldBe 0L
    }

    @Test
    fun `a custom value is returned unchanged on a large volume`() {
        LowStorage.resolveThreshold(
            capacityBytes = 512_000_000_000L,
            customThresholdBytes = 10_000_000_000L,
        ) shouldBe 10_000_000_000L
    }

    @Test
    fun `a custom value is returned unchanged on a small volume, without a capacity clamp`() {
        LowStorage.resolveThreshold(
            capacityBytes = 8_000_000_000L,
            customThresholdBytes = 10_000_000_000L,
        ) shouldBe 10_000_000_000L
    }

    @Test
    fun `a negative custom value clamps to zero`() {
        LowStorage.resolveThreshold(
            capacityBytes = 8_000_000_000L,
            customThresholdBytes = -1L,
        ) shouldBe 0L
    }

    @Test
    fun `above the threshold is not low`() {
        LowStorage.isLow(spaceFreeBytes = 1_001L, thresholdBytes = 1_000L) shouldBe false
    }

    @Test
    fun `exactly at the threshold is low`() {
        LowStorage.isLow(spaceFreeBytes = 1_000L, thresholdBytes = 1_000L) shouldBe true
    }

    @Test
    fun `below the threshold is low`() {
        LowStorage.isLow(spaceFreeBytes = 999L, thresholdBytes = 1_000L) shouldBe true
    }
}
