package eu.darken.sdmse.common.storage

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class VolumeInfoXTest : BaseTest() {

    class EmulatedVolume(private val id: String?) {
        fun getType(): Int = 2
        fun getId(): String? = id
    }

    private fun isRemovable(id: String?) = VolumeInfoX(EmulatedVolume(id)).isRemovable

    @Test fun `internal emulated storage is not removable`() {
        isRemovable("emulated") shouldBe false
        isRemovable("emulated;0") shouldBe false
    }

    @Test fun `other emulated storage is removable`() {
        isRemovable("emulated:8,3;0") shouldBe true
    }

    @Test fun `an emulated volume with an unknown id is not removable`() {
        isRemovable(null) shouldBe false
    }
}
