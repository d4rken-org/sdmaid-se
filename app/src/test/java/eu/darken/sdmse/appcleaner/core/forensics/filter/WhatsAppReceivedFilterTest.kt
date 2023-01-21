package eu.darken.sdmse.appcleaner.core.forensics.filter

import androidx.core.util.Pair
import eu.darken.sdmse.appcleaner.core.forensics.*
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
        dynamicSieveFactory = createDynamicSieveFactory()
    )

    // TODO refactor to non-legacy test methods
    // @formatter:off
    @Test fun testWhatsAppReceivedFilter() = runTest{
        addDefaultNegatives()
        for (p in PKGS) {
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree(p.second))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Video"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Video/Private"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Video/Sent/.nomedia"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Video/Private/.nomedia"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Video/Sent/testfile"))
            addCandidate(pos().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Video/VID-20140118-WA0000.mp4"))
            addCandidate(pos().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Video/Private/VID-20140118-WA0000.mp4"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Calls"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Calls/.nomedia"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Images"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Images/Private"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Images/Sent/.nomedia"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Images/Private/.nomedia"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Images/Sent/testfile"))
            addCandidate(pos().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Images/IMG-20131129-WA0001.jpg"))
            addCandidate(pos().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Images/Private/IMG-20131129-WA0001.jpg"))
            addCandidate(pos().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Images/IMG-20140725-WA0000.jpeg"))
            addCandidate(pos().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Images/Private/IMG-20140725-WA0000.jpeg"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Audio"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Audio/Private"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Audio/Sent/.nomedia"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Audio/Private/.nomedia"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Audio/Sent/testfile"))
            addCandidate(pos().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Audio/AUD-20151012-WA0000.aac"))
            addCandidate(pos().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Audio/Private/AUD-20151012-WA0000.aac"))
            addCandidate(pos().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Audio/AUD-20151205-WA0000.m4a"))
            addCandidate(pos().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Audio/Private/AUD-20151205-WA0000.m4a"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Documents"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Documents/Private"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Documents/Sent/.nomedia"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Documents/Private/.nomedia"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Documents/Sent/testfile"))
            addCandidate(pos().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Documents/Something123123!§($).pdf"))
            addCandidate(pos().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Documents/Private/Something123123!§($).pdf"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Voice Notes"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Voice Notes/.nomedia"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Voice Notes/201347"))
            addCandidate(pos().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Voice Notes/201347/PTT-20131121-WA0000.3ga"))
            addCandidate(pos().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Voice Notes/201547/PTT-20151118-WA0000.aac"))
            addCandidate(pos().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Voice Notes/201405/PTT-20140129-WA0000.amr"))
            addCandidate(pos().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Voice Notes/201540/6e435ac2dbd60fa01deb42ab538a02d7.1.aac"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Animated Gifs"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Animated Gifs/Private"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Animated Gifs/.nomedia"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Animated Gifs/Private/.nomedia"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Animated Gifs/Sent/testfile"))
            addCandidate(pos().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Animated Gifs/VID-20170330-WA0016.mp4"))
            addCandidate(pos().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Animated Gifs/Private/VID-20170330-WA0016.mp4"))
            addCandidate(pos().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Animated Gifs/VID-20170330-WA0016.gif"))
            addCandidate(pos().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Animated Gifs/Private/VID-20170330-WA0016.gif"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Video"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Video/Private"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Video/Sent/.nomedia"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Video/Private/.nomedia"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Video/Sent/testfile"))
            addCandidate(pos().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Video/VID-20140118-WA0000.mp4"))
            addCandidate(pos().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Video/Private/VID-20140118-WA0000.mp4"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Calls"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Calls/.nomedia"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Images"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Images/Private"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Images/Sent/.nomedia"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Images/Private/.nomedia"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Images/Sent/testfile"))
            addCandidate(pos().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Images/IMG-20131129-WA0001.jpg"))
            addCandidate(pos().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Images/Private/IMG-20131129-WA0001.jpg"))
            addCandidate(pos().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Images/IMG-20140725-WA0000.jpeg"))
            addCandidate(pos().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Images/Private/IMG-20140725-WA0000.jpeg"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Audio"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Audio/Private"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Audio/Sent/.nomedia"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Audio/Private/.nomedia"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Audio/Sent/testfile"))
            addCandidate(pos().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Audio/AUD-20151012-WA0000.aac"))
            addCandidate(pos().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Audio/Private/AUD-20151012-WA0000.aac"))
            addCandidate(pos().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Audio/AUD-20151205-WA0000.m4a"))
            addCandidate(pos().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Audio/Private/AUD-20151205-WA0000.m4a"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Documents"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Documents/Private"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Documents/Sent/.nomedia"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Documents/Private/.nomedia"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Documents/Sent/testfile"))
            addCandidate(pos().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Documents/Something123123!§($).pdf"))
            addCandidate(pos().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Documents/Private/Something123123!§($).pdf"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Voice Notes"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Voice Notes/.nomedia"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Voice Notes/201347"))
            addCandidate(pos().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Voice Notes/201347/PTT-20131121-WA0000.3ga"))
            addCandidate(pos().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Voice Notes/201547/PTT-20151118-WA0000.aac"))
            addCandidate(pos().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Voice Notes/201405/PTT-20140129-WA0000.amr"))
            addCandidate(pos().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Voice Notes/201540/6e435ac2dbd60fa01deb42ab538a02d7.1.aac"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Animated Gifs"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Animated Gifs/Private"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Animated Gifs/.nomedia"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Animated Gifs/Private/.nomedia"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Animated Gifs/Sent/testfile"))
            addCandidate(pos().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Animated Gifs/VID-20170330-WA0016.mp4"))
            addCandidate(pos().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Animated Gifs/Private/VID-20170330-WA0016.mp4"))
            addCandidate(pos().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Animated Gifs/VID-20170330-WA0016.gif"))
            addCandidate(pos().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Animated Gifs/Private/VID-20170330-WA0016.gif"))
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