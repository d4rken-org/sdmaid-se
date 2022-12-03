package eu.darken.sdmse.common.clutter.manual

import eu.darken.sdmse.common.areas.DataArea.Type.PRIVATE_DATA
import eu.darken.sdmse.common.areas.DataArea.Type.SDCARD
import eu.darken.sdmse.common.clutter.Marker
import eu.darken.sdmse.common.pkgs.toPkgId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test
import testhelpers.BaseTest

class ManualMarkerTest : BaseTest() {

    private val pkgs = setOf("test.pkg".toPkgId())

    @Test fun `equals checks case sensitivity`() {
        val flags = Marker.Flag.values().toSet()
        val upperCase = ManualMarker(pkgs, SDCARD, "PATH", "CONTAINS", "PATTERN", flags)
        val lowerCase = ManualMarker(pkgs, SDCARD, "path", "contains", "pattern", flags)
        upperCase shouldBe lowerCase
        val diffUpper = ManualMarker(pkgs, PRIVATE_DATA, "PATH", "CONTAINS", "PATTERN", flags)
        val diffLower = ManualMarker(pkgs, PRIVATE_DATA, "path", "contains", "pattern", flags)
        diffUpper shouldNotBe diffLower
    }

    @Test fun `hashcode checks case sensitivity`() {
        val flags = Marker.Flag.values().toSet()
        val upperCase = ManualMarker(pkgs, SDCARD, "PATH", "CONTAINS", "PATTERN", flags)
        val lowerCase = ManualMarker(pkgs, SDCARD, "path", "contains", "pattern", flags)
        upperCase.hashCode() shouldBe lowerCase.hashCode()
        val diffUpper = ManualMarker(pkgs, PRIVATE_DATA, "PATH", "CONTAINS", "PATTERN", flags)
        val diffLower = ManualMarker(pkgs, PRIVATE_DATA, "path", "contains", "pattern", flags)
        diffUpper.hashCode() shouldNotBe diffLower.hashCode()
    }

    @Test fun `patterns check case sensitivity`() {
        val nonSensitive = ManualMarker(pkgs, SDCARD, null, null, "pattern", emptySet())
        val sensitive = ManualMarker(pkgs, PRIVATE_DATA, null, null, "pattern", emptySet())
        nonSensitive.match(SDCARD, "PATTERN") shouldNotBe null
        nonSensitive.match(SDCARD, "pattern") shouldNotBe null
        sensitive.match(PRIVATE_DATA, "pattern") shouldNotBe null
        sensitive.match(PRIVATE_DATA, "PATTERN") shouldBe null
    }

    @Test fun `contains checks case sensitivity`() {
        val nonSensitive = ManualMarker(pkgs, SDCARD, "PATH", "AT", null, emptySet())
        val sensitive = ManualMarker(pkgs, PRIVATE_DATA, "path", "at", null, emptySet())
        nonSensitive.match(SDCARD, "PATH") shouldNotBe null
        nonSensitive.match(SDCARD, "PATH") shouldNotBe null
        sensitive.match(PRIVATE_DATA, "path") shouldNotBe null
        sensitive.match(PRIVATE_DATA, "PATH") shouldBe null
    }

}