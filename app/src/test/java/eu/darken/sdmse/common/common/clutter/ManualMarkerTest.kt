package eu.darken.sdmse.common.common.clutter

import eu.darken.sdmse.common.clutter.Marker
import eu.darken.sdmse.common.clutter.manual.ManualMarker
import eu.darken.sdmse.common.storageareas.StorageArea.Type.PRIVATE_DATA
import eu.darken.sdmse.common.storageareas.StorageArea.Type.SDCARD
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test
import testhelpers.BaseTest

class ManualMarkerTest : BaseTest() {

    @Test fun `equals checks case sensitivity`() {
        val flags = Marker.Flag.values().toSet()
        val upperCase = ManualMarker(setOf("test.pkg"), SDCARD, "PATH", "CONTAINS", "PATTERN", flags)
        val lowerCase = ManualMarker(setOf("test.pkg"), SDCARD, "path", "contains", "pattern", flags)
        upperCase shouldBe lowerCase
        val diffUpper = ManualMarker(setOf("test.pkg"), PRIVATE_DATA, "PATH", "CONTAINS", "PATTERN", flags)
        val diffLower = ManualMarker(setOf("test.pkg"), PRIVATE_DATA, "path", "contains", "pattern", flags)
        diffUpper shouldNotBe diffLower
    }

    @Test fun `hashcode checks case sensitivity`() {
        val flags = Marker.Flag.values().toSet()
        val upperCase = ManualMarker(setOf("test.pkg"), SDCARD, "PATH", "CONTAINS", "PATTERN", flags)
        val lowerCase = ManualMarker(setOf("test.pkg"), SDCARD, "path", "contains", "pattern", flags)
        upperCase.hashCode() shouldBe lowerCase.hashCode()
        val diffUpper = ManualMarker(setOf("test.pkg"), PRIVATE_DATA, "PATH", "CONTAINS", "PATTERN", flags)
        val diffLower = ManualMarker(setOf("test.pkg"), PRIVATE_DATA, "path", "contains", "pattern", flags)
        diffUpper.hashCode() shouldNotBe diffLower.hashCode()
    }

    @Test fun `patterns check case sensitivity`() {
        val nonSensitive = ManualMarker(setOf("test.pkg"), SDCARD, null, null, "pattern", emptySet())
        val sensitive = ManualMarker(setOf("test.pkg"), PRIVATE_DATA, null, null, "pattern", emptySet())
        nonSensitive.match(SDCARD, "PATTERN") shouldNotBe null
        nonSensitive.match(SDCARD, "pattern") shouldNotBe null
        sensitive.match(PRIVATE_DATA, "pattern") shouldNotBe null
        sensitive.match(PRIVATE_DATA, "PATTERN") shouldBe null
    }

    @Test fun `contains checks case sensitivity`() {
        val nonSensitive = ManualMarker(setOf("test.pkg"), SDCARD, "PATH", "AT", null, emptySet())
        val sensitive = ManualMarker(setOf("test.pkg"), PRIVATE_DATA, "path", "at", null, emptySet())
        nonSensitive.match(SDCARD, "PATH") shouldNotBe null
        nonSensitive.match(SDCARD, "PATH") shouldNotBe null
        sensitive.match(PRIVATE_DATA, "path") shouldNotBe null
        sensitive.match(PRIVATE_DATA, "PATH") shouldBe null
    }

}