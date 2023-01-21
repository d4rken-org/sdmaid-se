package eu.darken.sdmse.appcleaner.core.forensics.filter

import androidx.core.util.Pair
import eu.darken.sdmse.appcleaner.core.forensics.*
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
        dynamicSieveFactory = createDynamicSieveFactory()
    )

    // TODO refactor to non-legacy test methods
    // @formatter:off
    @Test fun testWhatsAppSentFilter() = runTest{
        addDefaultNegatives()
        for (p in PKGS) {
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree(p.second))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Calls"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Calls/testfile"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Calls/.nomedia"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Calls/Private/.nomedia"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Images"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Images/testfile"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Images/Private/testfile"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Images/Sent/.nomedia"))
            addCandidate(pos().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Images/Sent/testfile"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Audio"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Audio/testfile"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Audio/Private/testfile"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Audio/Sent/.nomedia"))
            addCandidate(pos().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Audio/Sent/testfile"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Video"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Video/testfile"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Video/Sent/.nomedia"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Video/Private/testFile"))
            addCandidate(pos().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Video/Sent/testfile"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Documents"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Documents/testfile"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Documents/Private/testfile"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Documents/Sent/.nomedia"))
            addCandidate(pos().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Documents/Sent/testfile"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Animated Gifs"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Animated Gifs/.nomedia"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Animated Gifs/testfile"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Animated Gifs/Private/testfile"))
            addCandidate(pos().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Animated Gifs/Sent/testfile"))
            addCandidate(pos().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Animated Gifs/Sent/VID-20170330-WA0016.mp4"))
            addCandidate(pos().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Animated Gifs/Sent/VID-20170330-WA0016.gif"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Voice Notes"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Voice Notes/201402/testfile"))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media/${p.second} Voice Notes/.nomedia"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Calls"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Calls/testfile"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Calls/.nomedia"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Calls/Private/.nomedia"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Images"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Images/testfile"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Images/Private/testfile"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Images/Sent/.nomedia"))
            addCandidate(pos().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Images/Sent/testfile"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Audio"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Audio/testfile"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Audio/Private/testfile"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Audio/Sent/.nomedia"))
            addCandidate(pos().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Audio/Sent/testfile"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Video"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Video/testfile"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Video/Sent/.nomedia"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Video/Private/testFile"))
            addCandidate(pos().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Video/Sent/testfile"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Documents"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Documents/testfile"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Documents/Private/testfile"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Documents/Sent/.nomedia"))
            addCandidate(pos().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Documents/Sent/testfile"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Animated Gifs"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Animated Gifs/.nomedia"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Animated Gifs/testfile"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Animated Gifs/Private/testfile"))
            addCandidate(pos().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Animated Gifs/Sent/testfile"))
            addCandidate(pos().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Animated Gifs/Sent/VID-20170330-WA0016.mp4"))
            addCandidate(pos().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Animated Gifs/Sent/VID-20170330-WA0016.gif"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Voice Notes"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Voice Notes/201402/testfile"))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.first}/${p.second}/Media/${p.second} Voice Notes/.nomedia"))
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