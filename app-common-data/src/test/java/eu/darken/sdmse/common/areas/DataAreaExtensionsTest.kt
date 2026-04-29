package eu.darken.sdmse.common.areas

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class DataAreaExtensionsTest : BaseTest() {

    @Test
    fun `isSensitiveRoot covers all public storage locations`() {
        val sensitive = listOf(
            DataArea.Type.SDCARD,
            DataArea.Type.PUBLIC_DATA,
            DataArea.Type.PUBLIC_OBB,
            DataArea.Type.PUBLIC_MEDIA,
            DataArea.Type.PORTABLE,
        )
        sensitive.forEach { it.isSensitiveRoot shouldBe true }
    }

    @Test
    fun `isSensitiveRoot is false for system and app-private locations`() {
        val benign = DataArea.Type.entries.filter { it !in DataArea.Type.PUBLIC_LOCATIONS }
        benign.forEach { it.isSensitiveRoot shouldBe false }
    }
}
