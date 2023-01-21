package eu.darken.sdmse.appcleaner.core.forensics.filter

import eu.darken.sdmse.appcleaner.core.forensics.*
import eu.darken.sdmse.common.areas.DataArea.Type.SDCARD
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ThreemaFilterTest : BaseFilterTest() {

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = ThreemaFilter(
        dynamicSieveFactory = createDynamicSieveFactory()
    )

    // TODO refactor to non-legacy test methods
    @Test fun testFilter() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("ch.threema.app").locs(SDCARD).prefixFree("Threema"))
        addCandidate(neg().pkgs("ch.threema.app").locs(SDCARD).prefixFree("Threema/Threema "))
        addCandidate(neg().pkgs("ch.threema.app").locs(SDCARD).prefixFree("WhatsApp/Threema Audio"))
        addCandidate(neg().pkgs("ch.threema.app").locs(SDCARD).prefixFree("WhatsApp/Threema Images"))
        addCandidate(neg().pkgs("ch.threema.app").locs(SDCARD).prefixFree("WhatsApp/Threema Videos"))
        addCandidate(neg().pkgs("ch.threema.app").locs(SDCARD).prefixFree("Threema"))
        addCandidate(neg().pkgs("ch.threema.app").locs(SDCARD).prefixFree("Threema/.nomedia"))
        addCandidate(neg().pkgs("ch.threema.app").locs(SDCARD).prefixFree("Threema/Threema "))
        addCandidate(neg().pkgs("ch.threema.app").locs(SDCARD).prefixFree("Threema/Threema Audio/.nomedia"))
        addCandidate(neg().pkgs("ch.threema.app").locs(SDCARD).prefixFree("Threema/Threema Pictures/.nomedia"))
        addCandidate(neg().pkgs("ch.threema.app").locs(SDCARD).prefixFree("Threema/Threema Videos/.nomedia"))
        addCandidate(
            pos().pkgs("ch.threema.app").locs(SDCARD).prefixFree("Threema/Threema Audio/1213123123_123123123asdasd.ogg")
        )
        addCandidate(
            pos().pkgs("ch.threema.app").locs(SDCARD).prefixFree("Threema/Threema Pictures/425705794_239071.jpg")
        )
        addCandidate(
            pos().pkgs("ch.threema.app").locs(SDCARD).prefixFree("Threema/Threema Videos/1_376646255079588440.mp4")
        )
        confirm(create())
    }
}