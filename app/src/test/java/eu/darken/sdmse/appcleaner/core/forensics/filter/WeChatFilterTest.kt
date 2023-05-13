package eu.darken.sdmse.appcleaner.core.forensics.filter

import eu.darken.sdmse.appcleaner.core.forensics.BaseFilterTest
import eu.darken.sdmse.appcleaner.core.forensics.addCandidate
import eu.darken.sdmse.appcleaner.core.forensics.locs
import eu.darken.sdmse.appcleaner.core.forensics.neg
import eu.darken.sdmse.appcleaner.core.forensics.pkgs
import eu.darken.sdmse.appcleaner.core.forensics.pos
import eu.darken.sdmse.appcleaner.core.forensics.prefixFree
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
        dynamicSieveFactory = createDynamicSieveFactory()
    )

    @Test fun testFilter() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("com.tencent.mm").locs(SDCARD).prefixFree("tencent"))
        addCandidate(neg().pkgs("com.tencent.mm").locs(SDCARD).prefixFree("tencent/MicroMsg"))
        addCandidate(
            neg().pkgs("com.tencent.mm").locs(SDCARD).prefixFree("tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3")
        )
        addCandidate(
            neg().pkgs("com.tencent.mm").locs(SDCARD)
                .prefixFree("tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/favorite")
        )
        addCandidate(neg().pkgs("com.tencent.mm").locs(SDCARD).prefixFree("tencent/MicroMsg/WeChat"))
        addCandidate(
            neg().pkgs("com.tencent.mm").locs(SDCARD)
                .prefixFree("tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/sns")
        )
        addCandidate(
            pos().pkgs("com.tencent.mm").locs(SDCARD)
                .prefixFree("tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/sns/$rngString")
        )
        addCandidate(
            neg().pkgs("com.tencent.mm").locs(SDCARD)
                .prefixFree("tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/video")
        )
        addCandidate(
            pos().pkgs("com.tencent.mm").locs(SDCARD)
                .prefixFree("tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/video/$rngString")
        )
        addCandidate(
            neg().pkgs("com.tencent.mm").locs(SDCARD)
                .prefixFree("tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/image2")
        )
        addCandidate(
            pos().pkgs("com.tencent.mm").locs(SDCARD)
                .prefixFree("tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/image2/$rngString")
        )
        addCandidate(
            neg().pkgs("com.tencent.mm").locs(SDCARD)
                .prefixFree("tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/voice2")
        )
        addCandidate(
            pos().pkgs("com.tencent.mm").locs(SDCARD)
                .prefixFree("tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/voice2/$rngString")
        )
        addCandidate(
            neg().pkgs("com.tencent.mm").locs(SDCARD)
                .prefixFree("tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/sns/.nomedia")
        )
        addCandidate(
            neg().pkgs("com.tencent.mm").locs(SDCARD)
                .prefixFree("tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/video/.nomedia")
        )
        addCandidate(
            neg().pkgs("com.tencent.mm").locs(SDCARD)
                .prefixFree("tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/image2/.nomedia")
        )
        addCandidate(
            neg().pkgs("com.tencent.mm").locs(SDCARD)
                .prefixFree("tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/voice2/.nomedia")
        )
        confirm(create())
    }
}