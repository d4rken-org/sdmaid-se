package eu.darken.sdmse.appcleaner.core.forensics.filter

import eu.darken.sdmse.appcleaner.core.forensics.BaseFilterTest
import eu.darken.sdmse.appcleaner.core.forensics.neg
import eu.darken.sdmse.appcleaner.core.forensics.pos
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

        neg("org.telegram.messenger", SDCARD, "Telegram")
        neg("org.telegram.messenger", SDCARD, "Telegram/Telegram ")
        neg("org.telegram.messenger", SDCARD, "WhatsApp/Telegram Audio")
        neg("org.telegram.messenger", SDCARD, "WhatsApp/Telegram Documents")
        neg("org.telegram.messenger", SDCARD, "WhatsApp/Telegram Images")
        neg("org.telegram.messenger", SDCARD, "WhatsApp/Telegram Video")
        neg("org.telegram.messenger", SDCARD, "Telegram")
        neg("org.telegram.messenger", SDCARD, "Telegram/.nomedia")
        neg("org.telegram.messenger", SDCARD, "Telegram/Telegram ")
        neg("org.telegram.messenger", SDCARD, "Telegram/Telegram Audio/.nomedia")
        neg("org.telegram.messenger", SDCARD, "Telegram/Telegram Documents/.nomedia")
        neg("org.telegram.messenger", SDCARD, "Telegram/Telegram Images/.nomedia")
        neg("org.telegram.messenger", SDCARD, "Telegram/Telegram Video/.nomedia")

        pos("org.telegram.messenger", SDCARD, "Telegram/Telegram Audio/1213123123_123123123asdasd.ogg")
        pos("org.telegram.messenger", SDCARD, "Telegram/Telegram Documents/1_376646255079588440.mp4")
        pos("org.telegram.messenger", SDCARD, "Telegram/Telegram Images/425705794_239071.jpg")
        pos("org.telegram.messenger", SDCARD, "Telegram/Telegram Video/1_376646255079588440.mp4")
        pos(
            "org.telegram.messenger",
            PUBLIC_DATA,
            "org.telegram.messenger/files/Telegram/Telegram Audio/1213123123_123123123asdasd.ogg"
        )
        pos(
            "org.telegram.messenger",
            PUBLIC_DATA,
            "org.telegram.messenger/files/Telegram/Telegram Documents/1_376646255079588440.mp4"
        )
        pos(
            "org.telegram.messenger",
            PUBLIC_DATA,
            "org.telegram.messenger/files/Telegram/Telegram Images/425705794_239071.jpg"
        )
        pos(
            "org.telegram.messenger",
            PUBLIC_DATA,
            "org.telegram.messenger/files/Telegram/Telegram Video/1_376646255079588440.mp4"
        )
        neg("org.telegram.messenger", PUBLIC_DATA, "org.telegram.messenger/files/Telegram")
        neg("org.telegram.messenger", PUBLIC_DATA, "org.telegram.messenger/files/Telegram/.nomedia")
        neg("org.telegram.messenger", PUBLIC_DATA, "org.telegram.messenger/files/Telegram/Telegram ")
        neg("org.telegram.messenger", PUBLIC_DATA, "org.telegram.messenger/files/Telegram/Telegram Audio/.nomedia")
        neg("org.telegram.messenger", PUBLIC_DATA, "org.telegram.messenger/files/Telegram/Telegram Documents/.nomedia")
        neg("org.telegram.messenger", PUBLIC_DATA, "org.telegram.messenger/files/Telegram/Telegram Images/.nomedia")
        neg("org.telegram.messenger", PUBLIC_DATA, "org.telegram.messenger/files/Telegram/Telegram Video/.nomedia")
        confirm(create())
    }

    @Test fun telegramPlus() = runTest {
        addDefaultNegatives()

        neg("org.telegram.plus", SDCARD, "Telegram")
        neg("org.telegram.plus", SDCARD, "Telegram/Telegram ")
        neg("org.telegram.plus", SDCARD, "WhatsApp/Telegram Audio")
        neg("org.telegram.plus", SDCARD, "WhatsApp/Telegram Documents")
        neg("org.telegram.plus", SDCARD, "WhatsApp/Telegram Images")
        neg("org.telegram.plus", SDCARD, "WhatsApp/Telegram Video")
        neg("org.telegram.plus", SDCARD, "Telegram")
        neg("org.telegram.plus", SDCARD, "Telegram/.nomedia")
        neg("org.telegram.plus", SDCARD, "Telegram/Telegram ")
        neg("org.telegram.plus", SDCARD, "Telegram/Telegram Audio/.nomedia")
        neg("org.telegram.plus", SDCARD, "Telegram/Telegram Documents/.nomedia")
        neg("org.telegram.plus", SDCARD, "Telegram/Telegram Images/.nomedia")
        neg("org.telegram.plus", SDCARD, "Telegram/Telegram Video/.nomedia")
        pos("org.telegram.plus", SDCARD, "Telegram/Telegram Audio/1213123123_123123123asdasd.ogg")
        pos("org.telegram.plus", SDCARD, "Telegram/Telegram Documents/1_376646255079588440.mp4")
        pos("org.telegram.plus", SDCARD, "Telegram/Telegram Images/425705794_239071.jpg")
        pos("org.telegram.plus", SDCARD, "Telegram/Telegram Video/1_376646255079588440.mp4")

        confirm(create())
    }

    @Test fun testClones() = runTest {
        addDefaultNegatives()

        neg("org.thunderdog.challegram", SDCARD, "Telegram")
        neg("org.thunderdog.challegram", SDCARD, "Telegram/Telegram ")
        neg("org.thunderdog.challegram", SDCARD, "WhatsApp/Telegram Audio")
        neg("org.thunderdog.challegram", SDCARD, "WhatsApp/Telegram Documents")
        neg("org.thunderdog.challegram", SDCARD, "WhatsApp/Telegram Images")
        neg("org.thunderdog.challegram", SDCARD, "WhatsApp/Telegram Video")
        neg("org.thunderdog.challegram", SDCARD, "Telegram")
        neg("org.thunderdog.challegram", SDCARD, "Telegram/.nomedia")
        neg("org.thunderdog.challegram", SDCARD, "Telegram/Telegram ")
        neg("org.thunderdog.challegram", SDCARD, "Telegram/Telegram Audio/.nomedia")
        neg("org.thunderdog.challegram", SDCARD, "Telegram/Telegram Documents/.nomedia")
        neg("org.thunderdog.challegram", SDCARD, "Telegram/Telegram Images/.nomedia")
        neg("org.thunderdog.challegram", SDCARD, "Telegram/Telegram Video/.nomedia")
        pos("org.thunderdog.challegram", SDCARD, "Telegram/Telegram Audio/1213123123_123123123asdasd.ogg")
        pos("org.thunderdog.challegram", SDCARD, "Telegram/Telegram Documents/1_376646255079588440.mp4")
        pos("org.thunderdog.challegram", SDCARD, "Telegram/Telegram Images/425705794_239071.jpg")
        pos("org.thunderdog.challegram", SDCARD, "Telegram/Telegram Video/1_376646255079588440.mp4")
        pos(
            "org.thunderdog.challegram",
            PUBLIC_DATA,
            "org.thunderdog.challegram/files/documents/Lawnchair-alpha_2013.apk"
        )
        pos("org.thunderdog.challegram", PUBLIC_DATA, "org.thunderdog.challegram/files/music/strawberry")
        pos("org.thunderdog.challegram", PUBLIC_DATA, "org.thunderdog.challegram/files/videos/strawberry")
        pos("org.thunderdog.challegram", PUBLIC_DATA, "org.thunderdog.challegram/files/video_notes/strawberry")
        pos(
            "org.thunderdog.challegram",
            PUBLIC_DATA,
            "org.thunderdog.challegram/files/animations/ScreenRec_20190107_225020~2.mp4"
        )
        pos("org.thunderdog.challegram", PUBLIC_DATA, "org.thunderdog.challegram/files/voice/strawberry")
        pos("org.thunderdog.challegram", PUBLIC_DATA, "org.thunderdog.challegram/files/photos/853536364_160037_(0).jpg")

        confirm(create())
    }

    @Test fun testGraphMessenger() = runTest {
        addDefaultNegatives()

        neg("ir.ilmili.telegraph", SDCARD, "Telegram")
        neg("ir.ilmili.telegraph", SDCARD, "Telegram/Telegram ")
        neg("ir.ilmili.telegraph", SDCARD, "WhatsApp/Telegram Audio")
        neg("ir.ilmili.telegraph", SDCARD, "WhatsApp/Telegram Documents")
        neg("ir.ilmili.telegraph", SDCARD, "WhatsApp/Telegram Images")
        neg("ir.ilmili.telegraph", SDCARD, "WhatsApp/Telegram Video")
        neg("ir.ilmili.telegraph", SDCARD, "Telegram")
        neg("ir.ilmili.telegraph", SDCARD, "Telegram/.nomedia")
        neg("ir.ilmili.telegraph", SDCARD, "Telegram/Telegram ")
        neg("ir.ilmili.telegraph", SDCARD, "Telegram/Telegram Audio/.nomedia")
        neg("ir.ilmili.telegraph", SDCARD, "Telegram/Telegram Documents/.nomedia")
        neg("ir.ilmili.telegraph", SDCARD, "Telegram/Telegram Images/.nomedia")
        neg("ir.ilmili.telegraph", SDCARD, "Telegram/Telegram Video/.nomedia")

        pos("ir.ilmili.telegraph", SDCARD, "Telegram/Telegram Audio/1213123123_123123123asdasd.ogg")
        pos("ir.ilmili.telegraph", SDCARD, "Telegram/Telegram Documents/1_376646255079588440.mp4")
        pos("ir.ilmili.telegraph", SDCARD, "Telegram/Telegram Images/425705794_239071.jpg")
        pos("ir.ilmili.telegraph", SDCARD, "Telegram/Telegram Video/1_376646255079588440.mp4")

        pos(
            "ir.ilmili.telegraph",
            PUBLIC_DATA,
            "ir.ilmili.telegraph/files/Telegram/Telegram Audio/1213123123_123123123asdasd.ogg"
        )
        pos(
            "ir.ilmili.telegraph",
            PUBLIC_DATA,
            "ir.ilmili.telegraph/files/Telegram/Telegram Documents/1_376646255079588440.mp4"
        )
        pos(
            "ir.ilmili.telegraph",
            PUBLIC_DATA,
            "ir.ilmili.telegraph/files/Telegram/Telegram Images/425705794_239071.jpg"
        )
        pos(
            "ir.ilmili.telegraph",
            PUBLIC_DATA,
            "ir.ilmili.telegraph/files/Telegram/Telegram Video/1_376646255079588440.mp4"
        )

        neg("ir.ilmili.telegraph", PUBLIC_DATA, "ir.ilmili.telegraph/files/Telegram")
        neg("ir.ilmili.telegraph", PUBLIC_DATA, "ir.ilmili.telegraph/files/Telegram/.nomedia")
        neg("ir.ilmili.telegraph", PUBLIC_DATA, "ir.ilmili.telegraph/files/Telegram/Telegram ")
        neg("ir.ilmili.telegraph", PUBLIC_DATA, "ir.ilmili.telegraph/files/Telegram/Telegram Audio/.nomedia")
        neg("ir.ilmili.telegraph", PUBLIC_DATA, "ir.ilmili.telegraph/files/Telegram/Telegram Documents/.nomedia")
        neg("ir.ilmili.telegraph", PUBLIC_DATA, "ir.ilmili.telegraph/files/Telegram/Telegram Images/.nomedia")
        neg("ir.ilmili.telegraph", PUBLIC_DATA, "ir.ilmili.telegraph/files/Telegram/Telegram Video/.nomedia")

        confirm(create())
    }

    @Test fun `telegram web variant`() = runTest {
        addDefaultNegatives()
        pos(
            "org.telegram.messenger.web",
            PUBLIC_DATA,
            "org.telegram.messenger.web/files/Telegram/Telegram Audio/1213123123_123123123asdasd.ogg"
        )
        pos(
            "org.telegram.messenger.web",
            PUBLIC_DATA,
            "org.telegram.messenger.web/files/Telegram/Telegram Documents/1_376646255079588440.mp4"
        )
        pos(
            "org.telegram.messenger.web",
            PUBLIC_DATA,
            "org.telegram.messenger.web/files/Telegram/Telegram Images/425705794_239071.jpg"
        )
        pos(
            "org.telegram.messenger.web",
            PUBLIC_DATA,
            "org.telegram.messenger.web/files/Telegram/Telegram Video/1_376646255079588440.mp4"
        )

        neg("org.telegram.messenger.web", PUBLIC_DATA, "org.telegram.messenger.web/files/Telegram")
        neg("org.telegram.messenger.web", PUBLIC_DATA, "org.telegram.messenger.web/files/Telegram/.nomedia")
        neg("org.telegram.messenger.web", PUBLIC_DATA, "org.telegram.messenger.web/files/Telegram/Telegram ")
        neg(
            "org.telegram.messenger.web",
            PUBLIC_DATA,
            "org.telegram.messenger.web/files/Telegram/Telegram Audio/.nomedia"
        )
        neg(
            "org.telegram.messenger.web",
            PUBLIC_DATA,
            "org.telegram.messenger.web/files/Telegram/Telegram Documents/.nomedia"
        )
        neg(
            "org.telegram.messenger.web",
            PUBLIC_DATA,
            "org.telegram.messenger.web/files/Telegram/Telegram Images/.nomedia"
        )
        neg(
            "org.telegram.messenger.web",
            PUBLIC_DATA,
            "org.telegram.messenger.web/files/Telegram/Telegram Video/.nomedia"
        )

        confirm(create())
    }
}