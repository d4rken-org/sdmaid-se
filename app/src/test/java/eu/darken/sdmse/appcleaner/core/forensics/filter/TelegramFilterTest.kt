package eu.darken.sdmse.appcleaner.core.forensics.filter

import eu.darken.sdmse.appcleaner.core.forensics.*
import eu.darken.sdmse.common.areas.DataArea.Type.PUBLIC_DATA
import eu.darken.sdmse.common.areas.DataArea.Type.SDCARD
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TelegramFilterTest : BaseFilterTest() {

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = TelegramFilter(
        dynamicSieveFactory = createDynamicSieveFactory()
    )

    @Test fun telegram() = runTest {
        addDefaultNegatives()
        val telegrams = arrayOf(
            "org.telegram.messenger"
        )
        addCandidate(neg().pkgs(*telegrams).locs(SDCARD).prefixFree("Telegram"))
        addCandidate(neg().pkgs(*telegrams).locs(SDCARD).prefixFree("Telegram/Telegram "))
        addCandidate(neg().pkgs(*telegrams).locs(SDCARD).prefixFree("WhatsApp/Telegram Audio"))
        addCandidate(neg().pkgs(*telegrams).locs(SDCARD).prefixFree("WhatsApp/Telegram Documents"))
        addCandidate(neg().pkgs(*telegrams).locs(SDCARD).prefixFree("WhatsApp/Telegram Images"))
        addCandidate(neg().pkgs(*telegrams).locs(SDCARD).prefixFree("WhatsApp/Telegram Video"))
        addCandidate(neg().pkgs(*telegrams).locs(SDCARD).prefixFree("Telegram"))
        addCandidate(neg().pkgs(*telegrams).locs(SDCARD).prefixFree("Telegram/.nomedia"))
        addCandidate(neg().pkgs(*telegrams).locs(SDCARD).prefixFree("Telegram/Telegram "))
        addCandidate(neg().pkgs(*telegrams).locs(SDCARD).prefixFree("Telegram/Telegram Audio/.nomedia"))
        addCandidate(neg().pkgs(*telegrams).locs(SDCARD).prefixFree("Telegram/Telegram Documents/.nomedia"))
        addCandidate(neg().pkgs(*telegrams).locs(SDCARD).prefixFree("Telegram/Telegram Images/.nomedia"))
        addCandidate(neg().pkgs(*telegrams).locs(SDCARD).prefixFree("Telegram/Telegram Video/.nomedia"))
        addCandidate(
            pos().pkgs(*telegrams).locs(SDCARD).prefixFree("Telegram/Telegram Audio/1213123123_123123123asdasd.ogg")
        )
        addCandidate(
            pos().pkgs(*telegrams).locs(SDCARD).prefixFree("Telegram/Telegram Documents/1_376646255079588440.mp4")
        )
        addCandidate(pos().pkgs(*telegrams).locs(SDCARD).prefixFree("Telegram/Telegram Images/425705794_239071.jpg"))
        addCandidate(pos().pkgs(*telegrams).locs(SDCARD).prefixFree("Telegram/Telegram Video/1_376646255079588440.mp4"))
        addCandidate(
            pos().pkgs(*telegrams).locs(PUBLIC_DATA)
                .prefixFree("org.telegram.messenger/files/Telegram/Telegram Audio/1213123123_123123123asdasd.ogg")
        )
        addCandidate(
            pos().pkgs(*telegrams).locs(PUBLIC_DATA)
                .prefixFree("org.telegram.messenger/files/Telegram/Telegram Documents/1_376646255079588440.mp4")
        )
        addCandidate(
            pos().pkgs(*telegrams).locs(PUBLIC_DATA)
                .prefixFree("org.telegram.messenger/files/Telegram/Telegram Images/425705794_239071.jpg")
        )
        addCandidate(
            pos().pkgs(*telegrams).locs(PUBLIC_DATA)
                .prefixFree("org.telegram.messenger/files/Telegram/Telegram Video/1_376646255079588440.mp4")
        )
        addCandidate(neg().pkgs(*telegrams).locs(PUBLIC_DATA).prefixFree("org.telegram.messenger/files/Telegram"))
        addCandidate(
            neg().pkgs(*telegrams).locs(PUBLIC_DATA).prefixFree("org.telegram.messenger/files/Telegram/.nomedia")
        )
        addCandidate(
            neg().pkgs(*telegrams).locs(PUBLIC_DATA).prefixFree("org.telegram.messenger/files/Telegram/Telegram ")
        )
        addCandidate(
            neg().pkgs(*telegrams).locs(PUBLIC_DATA)
                .prefixFree("org.telegram.messenger/files/Telegram/Telegram Audio/.nomedia")
        )
        addCandidate(
            neg().pkgs(*telegrams).locs(PUBLIC_DATA)
                .prefixFree("org.telegram.messenger/files/Telegram/Telegram Documents/.nomedia")
        )
        addCandidate(
            neg().pkgs(*telegrams).locs(PUBLIC_DATA)
                .prefixFree("org.telegram.messenger/files/Telegram/Telegram Images/.nomedia")
        )
        addCandidate(
            neg().pkgs(*telegrams).locs(PUBLIC_DATA)
                .prefixFree("org.telegram.messenger/files/Telegram/Telegram Video/.nomedia")
        )
        confirm(create())
    }

    @Test fun telegramPlus() = runTest {
        addDefaultNegatives()
        val plus = arrayOf(
            "org.telegram.plus"
        )
        addCandidate(neg().pkgs(*plus).locs(SDCARD).prefixFree("Telegram"))
        addCandidate(neg().pkgs(*plus).locs(SDCARD).prefixFree("Telegram/Telegram "))
        addCandidate(neg().pkgs(*plus).locs(SDCARD).prefixFree("WhatsApp/Telegram Audio"))
        addCandidate(neg().pkgs(*plus).locs(SDCARD).prefixFree("WhatsApp/Telegram Documents"))
        addCandidate(neg().pkgs(*plus).locs(SDCARD).prefixFree("WhatsApp/Telegram Images"))
        addCandidate(neg().pkgs(*plus).locs(SDCARD).prefixFree("WhatsApp/Telegram Video"))
        addCandidate(neg().pkgs(*plus).locs(SDCARD).prefixFree("Telegram"))
        addCandidate(neg().pkgs(*plus).locs(SDCARD).prefixFree("Telegram/.nomedia"))
        addCandidate(neg().pkgs(*plus).locs(SDCARD).prefixFree("Telegram/Telegram "))
        addCandidate(neg().pkgs(*plus).locs(SDCARD).prefixFree("Telegram/Telegram Audio/.nomedia"))
        addCandidate(neg().pkgs(*plus).locs(SDCARD).prefixFree("Telegram/Telegram Documents/.nomedia"))
        addCandidate(neg().pkgs(*plus).locs(SDCARD).prefixFree("Telegram/Telegram Images/.nomedia"))
        addCandidate(neg().pkgs(*plus).locs(SDCARD).prefixFree("Telegram/Telegram Video/.nomedia"))
        addCandidate(
            pos().pkgs(*plus).locs(SDCARD).prefixFree("Telegram/Telegram Audio/1213123123_123123123asdasd.ogg")
        )
        addCandidate(pos().pkgs(*plus).locs(SDCARD).prefixFree("Telegram/Telegram Documents/1_376646255079588440.mp4"))
        addCandidate(pos().pkgs(*plus).locs(SDCARD).prefixFree("Telegram/Telegram Images/425705794_239071.jpg"))
        addCandidate(pos().pkgs(*plus).locs(SDCARD).prefixFree("Telegram/Telegram Video/1_376646255079588440.mp4"))
        confirm(create())
    }

    @Test fun testClones() = runTest {
        val clones = arrayOf(
            "org.thunderdog.challegram"
        )
        addDefaultNegatives()
        addCandidate(neg().pkgs(*clones).locs(SDCARD).prefixFree("Telegram"))
        addCandidate(neg().pkgs(*clones).locs(SDCARD).prefixFree("Telegram/Telegram "))
        addCandidate(neg().pkgs(*clones).locs(SDCARD).prefixFree("WhatsApp/Telegram Audio"))
        addCandidate(neg().pkgs(*clones).locs(SDCARD).prefixFree("WhatsApp/Telegram Documents"))
        addCandidate(neg().pkgs(*clones).locs(SDCARD).prefixFree("WhatsApp/Telegram Images"))
        addCandidate(neg().pkgs(*clones).locs(SDCARD).prefixFree("WhatsApp/Telegram Video"))
        addCandidate(neg().pkgs(*clones).locs(SDCARD).prefixFree("Telegram"))
        addCandidate(neg().pkgs(*clones).locs(SDCARD).prefixFree("Telegram/.nomedia"))
        addCandidate(neg().pkgs(*clones).locs(SDCARD).prefixFree("Telegram/Telegram "))
        addCandidate(neg().pkgs(*clones).locs(SDCARD).prefixFree("Telegram/Telegram Audio/.nomedia"))
        addCandidate(neg().pkgs(*clones).locs(SDCARD).prefixFree("Telegram/Telegram Documents/.nomedia"))
        addCandidate(neg().pkgs(*clones).locs(SDCARD).prefixFree("Telegram/Telegram Images/.nomedia"))
        addCandidate(neg().pkgs(*clones).locs(SDCARD).prefixFree("Telegram/Telegram Video/.nomedia"))
        addCandidate(
            pos().pkgs(*clones).locs(SDCARD).prefixFree("Telegram/Telegram Audio/1213123123_123123123asdasd.ogg")
        )
        addCandidate(
            pos().pkgs(*clones).locs(SDCARD).prefixFree("Telegram/Telegram Documents/1_376646255079588440.mp4")
        )
        addCandidate(pos().pkgs(*clones).locs(SDCARD).prefixFree("Telegram/Telegram Images/425705794_239071.jpg"))
        addCandidate(pos().pkgs(*clones).locs(SDCARD).prefixFree("Telegram/Telegram Video/1_376646255079588440.mp4"))
        addCandidate(
            pos().pkgs(*clones).locs(PUBLIC_DATA)
                .prefixFree("org.thunderdog.challegram/files/documents/Lawnchair-alpha_2013.apk")
        )
        addCandidate(
            pos().pkgs(*clones).locs(PUBLIC_DATA).prefixFree("org.thunderdog.challegram/files/music/strawberry")
        )
        addCandidate(
            pos().pkgs(*clones).locs(PUBLIC_DATA).prefixFree("org.thunderdog.challegram/files/videos/strawberry")
        )
        addCandidate(
            pos().pkgs(*clones).locs(PUBLIC_DATA).prefixFree("org.thunderdog.challegram/files/video_notes/strawberry")
        )
        addCandidate(
            pos().pkgs(*clones).locs(PUBLIC_DATA)
                .prefixFree("org.thunderdog.challegram/files/animations/ScreenRec_20190107_225020~2.mp4")
        )
        addCandidate(
            pos().pkgs(*clones).locs(PUBLIC_DATA).prefixFree("org.thunderdog.challegram/files/voice/strawberry")
        )
        addCandidate(
            pos().pkgs(*clones).locs(PUBLIC_DATA)
                .prefixFree("org.thunderdog.challegram/files/photos/853536364_160037_(0).jpg")
        )
        confirm(create())
    }
}