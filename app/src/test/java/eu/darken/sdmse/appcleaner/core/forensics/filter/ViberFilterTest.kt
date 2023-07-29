package eu.darken.sdmse.appcleaner.core.forensics.filter

import eu.darken.sdmse.appcleaner.core.forensics.BaseFilterTest
import eu.darken.sdmse.appcleaner.core.forensics.neg
import eu.darken.sdmse.appcleaner.core.forensics.pos
import eu.darken.sdmse.common.areas.DataArea.Type.PUBLIC_DATA
import eu.darken.sdmse.common.rngString
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ViberFilterTest : BaseFilterTest() {

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = ViberFilter(
        dynamicSieveFactory = createDynamicSieveFactory()
    )

    @Test fun `Delete all files`() = runTest {
        addDefaultNegatives()

        neg("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/.converted_videos")
        neg("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/.converted_videos/.nomedia")
        pos("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/.converted_videos/$rngString")

        neg("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/.converted_gifs")
        neg("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/.converted_gifs/.nomedia")
        pos("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/.converted_gifs/$rngString")

        neg("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/.import")
        neg("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/.import/.nomedia")
        pos("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/.import/$rngString")

        neg("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/.image")
        neg("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/.image/.nomedia")
        pos("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/.image/$rngString")

        neg("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/.video")
        neg("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/.video/.nomedia")
        pos("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/.video/$rngString")

        neg("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/.gif")
        neg("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/.gif/.nomedia")
        pos("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/.gif/$rngString")

        neg("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/.ptt")
        neg("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/.ptt/.nomedia")
        pos("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/.ptt/$rngString")

        neg("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/.vptt")
        neg("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/.vptt/.nomedia")
        pos("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/.vptt/$rngString")

        confirm(create())
    }
}