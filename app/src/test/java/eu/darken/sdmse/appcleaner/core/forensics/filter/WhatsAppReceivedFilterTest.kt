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

class WhatsAppReceivedFilterTest : BaseFilterTest() {

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = WhatsAppReceivedFilter(
        dynamicSieveFactory = createDynamicSieve2Factory(),
        gatewaySwitch = gatewaySwitch,
    )

    // @formatter:off
    @Test fun testWhatsAppReceivedFilter() = runTest{
        addDefaultNegatives()
        for (p in PKGS) {
            neg(p.first,SDCARD,p.second)
            neg(p.first,SDCARD,"${p.second}/Media")
            neg(p.first,SDCARD,"${p.second}/Media/${p.second} Video")
            neg(p.first,SDCARD,"${p.second}/Media/${p.second} Video/Private")
            neg(p.first,SDCARD,"${p.second}/Media/${p.second} Video/Sent/.nomedia")
            neg(p.first,SDCARD,"${p.second}/Media/${p.second} Video/Private/.nomedia")
            neg(p.first,SDCARD,"${p.second}/Media/${p.second} Video/Sent/testfile")
            pos(p.first,SDCARD,"${p.second}/Media/${p.second} Video/VID-20140118-WA0000.mp4")
            pos(p.first,SDCARD,"${p.second}/Media/${p.second} Video/Private/VID-20140118-WA0000.mp4")
            neg(p.first,SDCARD,"${p.second}/Media/${p.second} Calls")
            neg(p.first,SDCARD,"${p.second}/Media/${p.second} Calls/.nomedia")
            neg(p.first,SDCARD,"${p.second}/Media/${p.second} Images")
            neg(p.first,SDCARD,"${p.second}/Media/${p.second} Images/Private")
            neg(p.first,SDCARD,"${p.second}/Media/${p.second} Images/Sent/.nomedia")
            neg(p.first,SDCARD,"${p.second}/Media/${p.second} Images/Private/.nomedia")
            neg(p.first,SDCARD,"${p.second}/Media/${p.second} Images/Sent/testfile")
            pos(p.first,SDCARD,"${p.second}/Media/${p.second} Images/IMG-20131129-WA0001.jpg")
            pos(p.first,SDCARD,"${p.second}/Media/${p.second} Images/Private/IMG-20131129-WA0001.jpg")
            pos(p.first,SDCARD,"${p.second}/Media/${p.second} Images/IMG-20140725-WA0000.jpeg")
            pos(p.first,SDCARD,"${p.second}/Media/${p.second} Images/Private/IMG-20140725-WA0000.jpeg")
            neg(p.first,SDCARD,"${p.second}/Media/${p.second} Audio")
            neg(p.first,SDCARD,"${p.second}/Media/${p.second} Audio/Private")
            neg(p.first,SDCARD,"${p.second}/Media/${p.second} Audio/Sent/.nomedia")
            neg(p.first,SDCARD,"${p.second}/Media/${p.second} Audio/Private/.nomedia")
            neg(p.first,SDCARD,"${p.second}/Media/${p.second} Audio/Sent/testfile")
            pos(p.first,SDCARD,"${p.second}/Media/${p.second} Audio/AUD-20151012-WA0000.aac")
            pos(p.first,SDCARD,"${p.second}/Media/${p.second} Audio/Private/AUD-20151012-WA0000.aac")
            pos(p.first,SDCARD,"${p.second}/Media/${p.second} Audio/AUD-20151205-WA0000.m4a")
            pos(p.first,SDCARD,"${p.second}/Media/${p.second} Audio/Private/AUD-20151205-WA0000.m4a")
            neg(p.first,SDCARD,"${p.second}/Media/${p.second} Documents")
            neg(p.first,SDCARD,"${p.second}/Media/${p.second} Documents/Private")
            neg(p.first,SDCARD,"${p.second}/Media/${p.second} Documents/Sent/.nomedia")
            neg(p.first,SDCARD,"${p.second}/Media/${p.second} Documents/Private/.nomedia")
            neg(p.first,SDCARD,"${p.second}/Media/${p.second} Documents/Sent/testfile")
            pos(p.first,SDCARD,"${p.second}/Media/${p.second} Documents/Something123123!§($).pdf")
            pos(p.first,SDCARD,"${p.second}/Media/${p.second} Documents/Private/Something123123!§($).pdf")
            neg(p.first,SDCARD,"${p.second}/Media/${p.second} Voice Notes")
            neg(p.first,SDCARD,"${p.second}/Media/${p.second} Voice Notes/.nomedia")
            neg(p.first,SDCARD,"${p.second}/Media/${p.second} Voice Notes/201347")
            pos(p.first,SDCARD,"${p.second}/Media/${p.second} Voice Notes/201347/PTT-20131121-WA0000.3ga")
            pos(p.first,SDCARD,"${p.second}/Media/${p.second} Voice Notes/201547/PTT-20151118-WA0000.aac")
            pos(p.first,SDCARD,"${p.second}/Media/${p.second} Voice Notes/201405/PTT-20140129-WA0000.amr")
            pos(p.first,SDCARD,"${p.second}/Media/${p.second} Voice Notes/201540/6e435ac2dbd60fa01deb42ab538a02d7.1.aac")
            neg(p.first,SDCARD,"${p.second}/Media/${p.second} Animated Gifs")
            neg(p.first,SDCARD,"${p.second}/Media/${p.second} Animated Gifs/Private")
            neg(p.first,SDCARD,"${p.second}/Media/${p.second} Animated Gifs/.nomedia")
            neg(p.first,SDCARD,"${p.second}/Media/${p.second} Animated Gifs/Private/.nomedia")
            neg(p.first,SDCARD,"${p.second}/Media/${p.second} Animated Gifs/Sent/testfile")
            pos(p.first,SDCARD,"${p.second}/Media/${p.second} Animated Gifs/VID-20170330-WA0016.mp4")
            pos(p.first,SDCARD,"${p.second}/Media/${p.second} Animated Gifs/Private/VID-20170330-WA0016.mp4")
            pos(p.first,SDCARD,"${p.second}/Media/${p.second} Animated Gifs/VID-20170330-WA0016.gif")
            pos(p.first,SDCARD,"${p.second}/Media/${p.second} Animated Gifs/Private/VID-20170330-WA0016.gif")
            neg(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}")
            neg(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media")
            neg(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Video")
            neg(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Video/Private")
            neg(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Video/Sent/.nomedia")
            neg(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Video/Private/.nomedia")
            neg(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Video/Sent/testfile")
            pos(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Video/VID-20140118-WA0000.mp4")
            pos(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Video/Private/VID-20140118-WA0000.mp4")
            neg(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Calls")
            neg(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Calls/.nomedia")
            neg(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Images")
            neg(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Images/Private")
            neg(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Images/Sent/.nomedia")
            neg(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Images/Private/.nomedia")
            neg(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Images/Sent/testfile")
            pos(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Images/IMG-20131129-WA0001.jpg")
            pos(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Images/Private/IMG-20131129-WA0001.jpg")
            pos(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Images/IMG-20140725-WA0000.jpeg")
            pos(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Images/Private/IMG-20140725-WA0000.jpeg")
            neg(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Audio")
            neg(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Audio/Private")
            neg(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Audio/Sent/.nomedia")
            neg(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Audio/Private/.nomedia")
            neg(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Audio/Sent/testfile")
            pos(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Audio/AUD-20151012-WA0000.aac")
            pos(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Audio/Private/AUD-20151012-WA0000.aac")
            pos(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Audio/AUD-20151205-WA0000.m4a")
            pos(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Audio/Private/AUD-20151205-WA0000.m4a")
            neg(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Documents")
            neg(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Documents/Private")
            neg(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Documents/Sent/.nomedia")
            neg(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Documents/Private/.nomedia")
            neg(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Documents/Sent/testfile")
            pos(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Documents/Something123123!§($).pdf")
            pos(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Documents/Private/Something123123!§($).pdf")
            neg(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Voice Notes")
            neg(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Voice Notes/.nomedia")
            neg(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Voice Notes/201347")
            pos(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Voice Notes/201347/PTT-20131121-WA0000.3ga")
            pos(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Voice Notes/201547/PTT-20151118-WA0000.aac")
            pos(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Voice Notes/201405/PTT-20140129-WA0000.amr")
            pos(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Voice Notes/201540/6e435ac2dbd60fa01deb42ab538a02d7.1.aac")
            neg(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Animated Gifs")
            neg(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Animated Gifs/Private")
            neg(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Animated Gifs/.nomedia")
            neg(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Animated Gifs/Private/.nomedia")
            neg(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Animated Gifs/Sent/testfile")
            pos(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Animated Gifs/VID-20170330-WA0016.mp4")
            pos(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Animated Gifs/Private/VID-20170330-WA0016.mp4")
            pos(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Animated Gifs/VID-20170330-WA0016.gif")
            pos(p.first,PUBLIC_MEDIA,"${p.first}/${p.second}/Media/${p.second} Animated Gifs/Private/VID-20170330-WA0016.gif")
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