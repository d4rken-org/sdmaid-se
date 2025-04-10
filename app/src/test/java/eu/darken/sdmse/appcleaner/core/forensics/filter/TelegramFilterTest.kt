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
        dynamicSieveFactory = createDynamicSieve2Factory(),
        gatewaySwitch = gatewaySwitch,
    )

    @Test fun `test telegram`() = runTest {
        addDefaultNegatives()
        val pkg = "org.telegram.messenger"
        neg(pkg, SDCARD, "Telegram")
        neg(pkg, SDCARD, "Telegram/Telegram ")
        neg(pkg, SDCARD, "WhatsApp/Telegram Audio")
        neg(pkg, SDCARD, "WhatsApp/Telegram Documents")
        neg(pkg, SDCARD, "WhatsApp/Telegram Images")
        neg(pkg, SDCARD, "WhatsApp/Telegram Video")
        neg(pkg, SDCARD, "Telegram")
        neg(pkg, SDCARD, "Telegram/.nomedia")
        neg(pkg, SDCARD, "Telegram/Telegram ")
        neg(pkg, SDCARD, "Telegram/Telegram Audio/.nomedia")
        neg(pkg, SDCARD, "Telegram/Telegram Documents/.nomedia")
        neg(pkg, SDCARD, "Telegram/Telegram Images/.nomedia")
        neg(pkg, SDCARD, "Telegram/Telegram Video/.nomedia")

        pos(pkg, SDCARD, "Telegram/Telegram Audio/1213123123_123123123asdasd.ogg")
        pos(pkg, SDCARD, "Telegram/Telegram Documents/1_376646255079588440.mp4")
        pos(pkg, SDCARD, "Telegram/Telegram Images/425705794_239071.jpg")
        pos(pkg, SDCARD, "Telegram/Telegram Video/1_376646255079588440.mp4")

        pos(pkg, SDCARD, "Telegram/Telegram Stories/1_376646255079588440.mp4")
        neg(pkg, SDCARD, "Telegram/Telegram Stories/.nomedia")
        neg(pkg, SDCARD, "Telegram/Telegram Stories")

        pos(pkg, PUBLIC_DATA, "$pkg/files/Telegram/Telegram Audio/1213123123_123123123asdasd.ogg")
        pos(pkg, PUBLIC_DATA, "$pkg/files/Telegram/Telegram Documents/1_376646255079588440.mp4")
        pos(pkg, PUBLIC_DATA, "$pkg/files/Telegram/Telegram Images/425705794_239071.jpg")
        pos(pkg, PUBLIC_DATA, "$pkg/files/Telegram/Telegram Video/1_376646255079588440.mp4")
        neg(pkg, PUBLIC_DATA, "$pkg/files/Telegram")
        neg(pkg, PUBLIC_DATA, "$pkg/files/Telegram/.nomedia")
        neg(pkg, PUBLIC_DATA, "$pkg/files/Telegram/Telegram ")
        neg(pkg, PUBLIC_DATA, "$pkg/files/Telegram/Telegram Audio/.nomedia")
        neg(pkg, PUBLIC_DATA, "$pkg/files/Telegram/Telegram Documents/.nomedia")
        neg(pkg, PUBLIC_DATA, "$pkg/files/Telegram/Telegram Images/.nomedia")
        neg(pkg, PUBLIC_DATA, "$pkg/files/Telegram/Telegram Video/.nomedia")

        pos(pkg, PUBLIC_DATA, "$pkg/files/Telegram/Telegram Stories/1_376646255079588440.mp4")
        neg(pkg, PUBLIC_DATA, "$pkg/files/Telegram/Telegram Stories/.nomedia")
        neg(pkg, PUBLIC_DATA, "$pkg/files/Telegram/Telegram Stories")

        confirm(create())
    }

    @Test fun `test telegram plus`() = runTest {
        addDefaultNegatives()
        val pkg = "org.telegram.plus"
        neg(pkg, SDCARD, "Telegram")
        neg(pkg, SDCARD, "Telegram/Telegram ")
        neg(pkg, SDCARD, "WhatsApp/Telegram Audio")
        neg(pkg, SDCARD, "WhatsApp/Telegram Documents")
        neg(pkg, SDCARD, "WhatsApp/Telegram Images")
        neg(pkg, SDCARD, "WhatsApp/Telegram Video")
        neg(pkg, SDCARD, "Telegram")
        neg(pkg, SDCARD, "Telegram/.nomedia")
        neg(pkg, SDCARD, "Telegram/Telegram ")
        neg(pkg, SDCARD, "Telegram/Telegram Audio/.nomedia")
        neg(pkg, SDCARD, "Telegram/Telegram Documents/.nomedia")
        neg(pkg, SDCARD, "Telegram/Telegram Images/.nomedia")
        neg(pkg, SDCARD, "Telegram/Telegram Video/.nomedia")
        pos(pkg, SDCARD, "Telegram/Telegram Audio/1213123123_123123123asdasd.ogg")
        pos(pkg, SDCARD, "Telegram/Telegram Documents/1_376646255079588440.mp4")
        pos(pkg, SDCARD, "Telegram/Telegram Images/425705794_239071.jpg")
        pos(pkg, SDCARD, "Telegram/Telegram Video/1_376646255079588440.mp4")

        pos(pkg, SDCARD, "Telegram/Telegram Stories/1_376646255079588440.mp4")
        neg(pkg, SDCARD, "Telegram/Telegram Stories/.nomedia")
        neg(pkg, SDCARD, "Telegram/Telegram Stories")

        confirm(create())
    }

    @Test fun `test telegram clones`() = runTest {
        addDefaultNegatives()
        val pkg = "org.thunderdog.challegram"
        neg(pkg, SDCARD, "Telegram")
        neg(pkg, SDCARD, "Telegram/Telegram ")
        neg(pkg, SDCARD, "WhatsApp/Telegram Audio")
        neg(pkg, SDCARD, "WhatsApp/Telegram Documents")
        neg(pkg, SDCARD, "WhatsApp/Telegram Images")
        neg(pkg, SDCARD, "WhatsApp/Telegram Video")
        neg(pkg, SDCARD, "Telegram")
        neg(pkg, SDCARD, "Telegram/.nomedia")
        neg(pkg, SDCARD, "Telegram/Telegram ")
        neg(pkg, SDCARD, "Telegram/Telegram Audio/.nomedia")
        neg(pkg, SDCARD, "Telegram/Telegram Documents/.nomedia")
        neg(pkg, SDCARD, "Telegram/Telegram Images/.nomedia")
        neg(pkg, SDCARD, "Telegram/Telegram Video/.nomedia")
        pos(pkg, SDCARD, "Telegram/Telegram Audio/1213123123_123123123asdasd.ogg")
        pos(pkg, SDCARD, "Telegram/Telegram Documents/1_376646255079588440.mp4")
        pos(pkg, SDCARD, "Telegram/Telegram Images/425705794_239071.jpg")
        pos(pkg, SDCARD, "Telegram/Telegram Video/1_376646255079588440.mp4")

        pos(pkg, SDCARD, "Telegram/Telegram Stories/1_376646255079588440.mp4")
        neg(pkg, SDCARD, "Telegram/Telegram Stories/.nomedia")
        neg(pkg, SDCARD, "Telegram/Telegram Stories")

        pos(pkg, PUBLIC_DATA, "$pkg/files/documents/Lawnchair-alpha_2013.apk")
        pos(pkg, PUBLIC_DATA, "$pkg/files/music/strawberry")
        pos(pkg, PUBLIC_DATA, "$pkg/files/videos/strawberry")
        pos(pkg, PUBLIC_DATA, "$pkg/files/video_notes/strawberry")
        pos(pkg, PUBLIC_DATA, "$pkg/files/animations/ScreenRec_20190107_225020~2.mp4")
        pos(pkg, PUBLIC_DATA, "$pkg/files/voice/strawberry")
        pos(pkg, PUBLIC_DATA, "$pkg/files/photos/853536364_160037_(0).jpg")

        pos(pkg, PUBLIC_DATA, "$pkg/files/stories/1_376646255079588440.mp4")
        neg(pkg, PUBLIC_DATA, "$pkg/files/stories/.nomedia")
        neg(pkg, PUBLIC_DATA, "$pkg/files/stories")

        confirm(create())
    }

    @Test fun `test telegram graph messenger`() = runTest {
        addDefaultNegatives()
        val pkg = "ir.ilmili.telegraph"
        neg(pkg, SDCARD, "Telegram")
        neg(pkg, SDCARD, "Telegram/Telegram ")
        neg(pkg, SDCARD, "WhatsApp/Telegram Audio")
        neg(pkg, SDCARD, "WhatsApp/Telegram Documents")
        neg(pkg, SDCARD, "WhatsApp/Telegram Images")
        neg(pkg, SDCARD, "WhatsApp/Telegram Video")
        neg(pkg, SDCARD, "Telegram")
        neg(pkg, SDCARD, "Telegram/.nomedia")
        neg(pkg, SDCARD, "Telegram/Telegram ")
        neg(pkg, SDCARD, "Telegram/Telegram Audio/.nomedia")
        neg(pkg, SDCARD, "Telegram/Telegram Documents/.nomedia")
        neg(pkg, SDCARD, "Telegram/Telegram Images/.nomedia")
        neg(pkg, SDCARD, "Telegram/Telegram Video/.nomedia")

        pos(pkg, SDCARD, "Telegram/Telegram Audio/1213123123_123123123asdasd.ogg")
        pos(pkg, SDCARD, "Telegram/Telegram Documents/1_376646255079588440.mp4")
        pos(pkg, SDCARD, "Telegram/Telegram Images/425705794_239071.jpg")
        pos(pkg, SDCARD, "Telegram/Telegram Video/1_376646255079588440.mp4")

        pos(pkg, SDCARD, "Telegram/Telegram Stories/1_376646255079588440.mp4")
        neg(pkg, SDCARD, "Telegram/Telegram Stories/.nomedia")
        neg(pkg, SDCARD, "Telegram/Telegram Stories")

        pos(pkg, PUBLIC_DATA, "$pkg/files/Telegram/Telegram Audio/1213123123_123123123asdasd.ogg")
        pos(pkg, PUBLIC_DATA, "$pkg/files/Telegram/Telegram Documents/1_376646255079588440.mp4")
        pos(pkg, PUBLIC_DATA, "$pkg/files/Telegram/Telegram Images/425705794_239071.jpg")
        pos(pkg, PUBLIC_DATA, "$pkg/files/Telegram/Telegram Video/1_376646255079588440.mp4")

        neg(pkg, PUBLIC_DATA, "$pkg/files/Telegram")
        neg(pkg, PUBLIC_DATA, "$pkg/files/Telegram/.nomedia")
        neg(pkg, PUBLIC_DATA, "$pkg/files/Telegram/Telegram ")
        neg(pkg, PUBLIC_DATA, "$pkg/files/Telegram/Telegram Audio/.nomedia")
        neg(pkg, PUBLIC_DATA, "$pkg/files/Telegram/Telegram Documents/.nomedia")
        neg(pkg, PUBLIC_DATA, "$pkg/files/Telegram/Telegram Images/.nomedia")
        neg(pkg, PUBLIC_DATA, "$pkg/files/Telegram/Telegram Video/.nomedia")

        pos(pkg, PUBLIC_DATA, "$pkg/files/Telegram/Telegram Stories/1_376646255079588440.mp4")
        neg(pkg, PUBLIC_DATA, "$pkg/files/Telegram/Telegram Stories/.nomedia")
        neg(pkg, PUBLIC_DATA, "$pkg/files/Telegram/Telegram Stories")

        confirm(create())
    }

    @Test fun `telegram web variant`() = runTest {
        addDefaultNegatives()
        val pkg = "org.telegram.messenger.web"
        pos(pkg, PUBLIC_DATA, "$pkg/files/Telegram/Telegram Audio/1213123123_123123123asdasd.ogg")
        pos(pkg, PUBLIC_DATA, "$pkg/files/Telegram/Telegram Documents/1_376646255079588440.mp4")
        pos(pkg, PUBLIC_DATA, "$pkg/files/Telegram/Telegram Images/425705794_239071.jpg")
        pos(pkg, PUBLIC_DATA, "$pkg/files/Telegram/Telegram Video/1_376646255079588440.mp4")

        neg(pkg, PUBLIC_DATA, "$pkg/files/Telegram")
        neg(pkg, PUBLIC_DATA, "$pkg/files/Telegram/.nomedia")
        neg(pkg, PUBLIC_DATA, "$pkg/files/Telegram/Telegram ")
        neg(pkg, PUBLIC_DATA, "$pkg/files/Telegram/Telegram Audio/.nomedia")
        neg(pkg, PUBLIC_DATA, "$pkg/files/Telegram/Telegram Documents/.nomedia")
        neg(pkg, PUBLIC_DATA, "$pkg/files/Telegram/Telegram Images/.nomedia")
        neg(pkg, PUBLIC_DATA, "$pkg/files/Telegram/Telegram Video/.nomedia")

        pos(pkg, PUBLIC_DATA, "$pkg/files/Telegram/Telegram Stories/1_376646255079588440.mp4")
        neg(pkg, PUBLIC_DATA, "$pkg/files/Telegram/Telegram Stories/.nomedia")
        neg(pkg, PUBLIC_DATA, "$pkg/files/Telegram/Telegram Stories")

        confirm(create())
    }
}