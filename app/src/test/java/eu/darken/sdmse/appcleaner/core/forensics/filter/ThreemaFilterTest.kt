package eu.darken.sdmse.appcleaner.core.forensics.filter

import eu.darken.sdmse.appcleaner.core.forensics.BaseFilterTest
import eu.darken.sdmse.appcleaner.core.forensics.neg
import eu.darken.sdmse.appcleaner.core.forensics.pos
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
        dynamicSieveFactory = createDynamicSieve2Factory(),
        gatewaySwitch = gatewaySwitch,
    )

    @Test fun testFilter() = runTest {
        addDefaultNegatives()
        val pkg = "ch.threema.app"
        pos(pkg, SDCARD, "Threema/Threema Audio/1213123123_123123123asdasd.ogg")
        pos(pkg, SDCARD, "Threema/Threema Pictures/425705794_239071.jpg")
        pos(pkg, SDCARD, "Threema/Threema Videos/1_376646255079588440.mp4")

        neg(pkg, SDCARD, "Threema")
        neg(pkg, SDCARD, "Threema/.nomedia")
        neg(pkg, SDCARD, "Threema/Threema ")
        neg(pkg, SDCARD, "Threema/Threema Audio/.nomedia")
        neg(pkg, SDCARD, "Threema/Threema Pictures/.nomedia")
        neg(pkg, SDCARD, "Threema/Threema Videos/.nomedia")
        neg(pkg, SDCARD, "WhatsApp/Threema Audio")
        neg(pkg, SDCARD, "WhatsApp/Threema Images")
        neg(pkg, SDCARD, "WhatsApp/Threema Videos")

        confirm(create())
    }
}