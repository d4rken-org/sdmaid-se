package eu.darken.sdmse.appcleaner.core.forensics.filter

import eu.darken.sdmse.appcleaner.core.forensics.BaseFilterTest
import eu.darken.sdmse.appcleaner.core.forensics.neg
import eu.darken.sdmse.appcleaner.core.forensics.pos
import eu.darken.sdmse.common.areas.DataArea.Type.PRIVATE_DATA
import eu.darken.sdmse.common.areas.DataArea.Type.PUBLIC_DATA
import eu.darken.sdmse.common.areas.DataArea.Type.SDCARD
import eu.darken.sdmse.common.rngString
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WeChatFilterTest : BaseFilterTest() {

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = WeChatFilter(
        dynamicSieveFactory = createDynamicSieve2Factory(),
        gatewaySwitch = gatewaySwitch,
    )

    @Test fun `test wechat filter`() = runTest {
        addDefaultNegatives()

        neg("com.tencent.mm", SDCARD, "tencent")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/favorite")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/WeChat")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/sns")
        pos("com.tencent.mm", SDCARD, "tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/sns/$rngString")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/video")
        pos("com.tencent.mm", SDCARD, "tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/video/$rngString")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/image2")
        neg("com.tencent.mm", SDCARD, "asd/def/efda91e6a3cd8c46008e42a3d3d614a3/image2/$rngString")
        pos("com.tencent.mm", SDCARD, "tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/image2/$rngString")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/voice2")
        pos("com.tencent.mm", SDCARD, "tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/voice2/$rngString")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/sns/.nomedia")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/video/.nomedia")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/image2/.nomedia")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/voice2/.nomedia")

        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm")
        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg")
        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3")
        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/favorite")
        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/WeChat")
        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/sns")
        pos("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/sns/$rngString")
        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/video")
        pos("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/video/$rngString")
        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/image2")
        pos("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/image2/$rngString")
        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/voice2")
        pos("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/voice2/$rngString")
        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/sns/.nomedia")
        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/video/.nomedia")
        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/image2/.nomedia")
        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/voice2/.nomedia")

        neg("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm")
        neg("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg")
        neg("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3")
        neg("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/favorite")
        neg("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/WeChat")
        neg("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/sns")
        pos("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/sns/$rngString")
        neg("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/video")
        pos("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/video/$rngString")
        neg("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/image2")
        pos(
            "com.tencent.mm",
            PRIVATE_DATA,
            "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/image2/$rngString"
        )
        neg("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/voice2")
        pos(
            "com.tencent.mm",
            PRIVATE_DATA,
            "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/voice2/$rngString"
        )
        neg("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/sns/.nomedia")
        neg("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/video/.nomedia")
        neg("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/image2/.nomedia")
        neg("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/voice2/.nomedia")

        confirm(create())
    }
}