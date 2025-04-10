package eu.darken.sdmse.appcleaner.core.forensics.filter

import androidx.core.util.Pair
import eu.darken.sdmse.appcleaner.core.forensics.BaseFilterTest
import eu.darken.sdmse.appcleaner.core.forensics.neg
import eu.darken.sdmse.appcleaner.core.forensics.pos
import eu.darken.sdmse.common.areas.DataArea.Type.PUBLIC_MEDIA
import eu.darken.sdmse.common.areas.DataArea.Type.SDCARD
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WhatsAppSentFilterTest : BaseFilterTest() {

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = WhatsAppSentFilter(
        dynamicSieveFactory = createDynamicSieve2Factory(),
        gatewaySwitch = gatewaySwitch,
    )

    // @formatter:off
    @Test fun `test whatsapp sent filter`() = runTest{
        addDefaultNegatives()
        for (p in PKGS) {
            neg(p.first, SDCARD, p.second)
            neg(p.first, SDCARD, "${p.second}/Media")
            neg(p.first, SDCARD, "${p.second}/Media/${p.second} Calls")
            neg(p.first, SDCARD, "${p.second}/Media/${p.second} Calls/testfile")
            neg(p.first, SDCARD, "${p.second}/Media/${p.second} Calls/.nomedia")
            neg(p.first, SDCARD, "${p.second}/Media/${p.second} Calls/Private/.nomedia")
            neg(p.first, SDCARD, "${p.second}/Media/${p.second} Images")
            neg(p.first, SDCARD, "${p.second}/Media/${p.second} Images/testfile")
            neg(p.first, SDCARD, "${p.second}/Media/${p.second} Images/Private/testfile")
            neg(p.first, SDCARD, "${p.second}/Media/${p.second} Images/Sent/.nomedia")
            pos(p.first, SDCARD, "${p.second}/Media/${p.second} Images/Sent/testfile")
            neg(p.first, SDCARD, "${p.second}/Media/${p.second} Audio")
            neg(p.first, SDCARD, "${p.second}/Media/${p.second} Audio/testfile")
            neg(p.first, SDCARD, "${p.second}/Media/${p.second} Audio/Private/testfile")
            neg(p.first, SDCARD, "${p.second}/Media/${p.second} Audio/Sent/.nomedia")
            pos(p.first, SDCARD, "${p.second}/Media/${p.second} Audio/Sent/testfile")
            neg(p.first, SDCARD, "${p.second}/Media/${p.second} Video")
            neg(p.first, SDCARD, "${p.second}/Media/${p.second} Video/testfile")
            neg(p.first, SDCARD, "${p.second}/Media/${p.second} Video/Sent/.nomedia")
            neg(p.first, SDCARD, "${p.second}/Media/${p.second} Video/Private/testFile")
            pos(p.first, SDCARD, "${p.second}/Media/${p.second} Video/Sent/testfile")
            neg(p.first, SDCARD, "${p.second}/Media/${p.second} Documents")
            neg(p.first, SDCARD, "${p.second}/Media/${p.second} Documents/testfile")
            neg(p.first, SDCARD, "${p.second}/Media/${p.second} Documents/Private/testfile")
            neg(p.first, SDCARD, "${p.second}/Media/${p.second} Documents/Sent/.nomedia")
            pos(p.first, SDCARD, "${p.second}/Media/${p.second} Documents/Sent/testfile")
            neg(p.first, SDCARD, "${p.second}/Media/${p.second} Animated Gifs")
            neg(p.first, SDCARD, "${p.second}/Media/${p.second} Animated Gifs/.nomedia")
            neg(p.first, SDCARD, "${p.second}/Media/${p.second} Animated Gifs/testfile")
            neg(p.first, SDCARD, "${p.second}/Media/${p.second} Animated Gifs/Private/testfile")
            pos(p.first, SDCARD, "${p.second}/Media/${p.second} Animated Gifs/Sent/testfile")
            pos(p.first, SDCARD, "${p.second}/Media/${p.second} Animated Gifs/Sent/VID-20170330-WA0016.mp4")
            pos(p.first, SDCARD, "${p.second}/Media/${p.second} Animated Gifs/Sent/VID-20170330-WA0016.gif")
            neg(p.first, SDCARD, "${p.second}/Media/${p.second} Voice Notes")
            neg(p.first, SDCARD, "${p.second}/Media/${p.second} Voice Notes/201402/testfile")
            neg(p.first, SDCARD, "${p.second}/Media/${p.second} Voice Notes/.nomedia")
            neg(p.first, PUBLIC_MEDIA, "${p.first}/${p.second}")
            neg(p.first, PUBLIC_MEDIA, "${p.first}/${p.second}/Media")
            neg(p.first, PUBLIC_MEDIA, "${p.first}/${p.second}/Media/${p.second} Calls")
            neg(p.first, PUBLIC_MEDIA, "${p.first}/${p.second}/Media/${p.second} Calls/testfile")
            neg(p.first, PUBLIC_MEDIA, "${p.first}/${p.second}/Media/${p.second} Calls/.nomedia")
            neg(p.first, PUBLIC_MEDIA, "${p.first}/${p.second}/Media/${p.second} Calls/Private/.nomedia")
            neg(p.first, PUBLIC_MEDIA, "${p.first}/${p.second}/Media/${p.second} Images")
            neg(p.first, PUBLIC_MEDIA, "${p.first}/${p.second}/Media/${p.second} Images/testfile")
            neg(p.first, PUBLIC_MEDIA, "${p.first}/${p.second}/Media/${p.second} Images/Private/testfile")
            neg(p.first, PUBLIC_MEDIA, "${p.first}/${p.second}/Media/${p.second} Images/Sent/.nomedia")
            pos(p.first, PUBLIC_MEDIA, "${p.first}/${p.second}/Media/${p.second} Images/Sent/testfile")
            neg(p.first, PUBLIC_MEDIA, "${p.first}/${p.second}/Media/${p.second} Audio")
            neg(p.first, PUBLIC_MEDIA, "${p.first}/${p.second}/Media/${p.second} Audio/testfile")
            neg(p.first, PUBLIC_MEDIA, "${p.first}/${p.second}/Media/${p.second} Audio/Private/testfile")
            neg(p.first, PUBLIC_MEDIA, "${p.first}/${p.second}/Media/${p.second} Audio/Sent/.nomedia")
            pos(p.first, PUBLIC_MEDIA, "${p.first}/${p.second}/Media/${p.second} Audio/Sent/testfile")
            neg(p.first, PUBLIC_MEDIA, "${p.first}/${p.second}/Media/${p.second} Video")
            neg(p.first, PUBLIC_MEDIA, "${p.first}/${p.second}/Media/${p.second} Video/testfile")
            neg(p.first, PUBLIC_MEDIA, "${p.first}/${p.second}/Media/${p.second} Video/Sent/.nomedia")
            neg(p.first, PUBLIC_MEDIA, "${p.first}/${p.second}/Media/${p.second} Video/Private/testFile")
            pos(p.first, PUBLIC_MEDIA, "${p.first}/${p.second}/Media/${p.second} Video/Sent/testfile")
            neg(p.first, PUBLIC_MEDIA, "${p.first}/${p.second}/Media/${p.second} Documents")
            neg(p.first, PUBLIC_MEDIA, "${p.first}/${p.second}/Media/${p.second} Documents/testfile")
            neg(p.first, PUBLIC_MEDIA, "${p.first}/${p.second}/Media/${p.second} Documents/Private/testfile")
            neg(p.first, PUBLIC_MEDIA, "${p.first}/${p.second}/Media/${p.second} Documents/Sent/.nomedia")
            pos(p.first, PUBLIC_MEDIA, "${p.first}/${p.second}/Media/${p.second} Documents/Sent/testfile")
            neg(p.first, PUBLIC_MEDIA, "${p.first}/${p.second}/Media/${p.second} Animated Gifs")
            neg(p.first, PUBLIC_MEDIA, "${p.first}/${p.second}/Media/${p.second} Animated Gifs/.nomedia")
            neg(p.first, PUBLIC_MEDIA, "${p.first}/${p.second}/Media/${p.second} Animated Gifs/testfile")
            neg(p.first, PUBLIC_MEDIA, "${p.first}/${p.second}/Media/${p.second} Animated Gifs/Private/testfile")
            pos(p.first, PUBLIC_MEDIA, "${p.first}/${p.second}/Media/${p.second} Animated Gifs/Sent/testfile")
            pos(p.first, PUBLIC_MEDIA, "${p.first}/${p.second}/Media/${p.second} Animated Gifs/Sent/VID-20170330-WA0016.mp4")
            pos(p.first, PUBLIC_MEDIA, "${p.first}/${p.second}/Media/${p.second} Animated Gifs/Sent/VID-20170330-WA0016.gif")
            neg(p.first, PUBLIC_MEDIA, "${p.first}/${p.second}/Media/${p.second} Voice Notes")
            neg(p.first, PUBLIC_MEDIA, "${p.first}/${p.second}/Media/${p.second} Voice Notes/201402/testfile")
            neg(p.first, PUBLIC_MEDIA, "${p.first}/${p.second}/Media/${p.second} Voice Notes/.nomedia")
        }
        confirm(create())
    }
    // @formatter:on

    companion object {
        private val PKGS = listOf(
            Pair.create("com.whatsapp", "WhatsApp"),
            Pair.create("com.whatsapp.w4b", "WhatsApp Business")
        )
    }
}