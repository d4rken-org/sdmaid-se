package eu.darken.sdmse.appcleaner.core.forensics.filter

import eu.darken.sdmse.appcleaner.core.forensics.BaseFilterTest
import eu.darken.sdmse.appcleaner.core.forensics.neg
import eu.darken.sdmse.appcleaner.core.forensics.pos
import eu.darken.sdmse.common.areas.DataArea.Type.PUBLIC_DATA
import eu.darken.sdmse.common.areas.DataArea.Type.SDCARD
import eu.darken.sdmse.common.rngString
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MobileQQFilterTest : BaseFilterTest() {

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = MobileQQFilter(
        dynamicSieveFactory = createDynamicSieveFactory(),
        gatewaySwitch = gatewaySwitch,
    )

    @Test fun testFilter() = runTest {
        addDefaultNegatives()

        neg("com.tencent.mobileqq", SDCARD, "tencent/MobileQQ/chatpic")
        pos("com.tencent.mobileqq", SDCARD, "tencent/MobileQQ/chatpic/$rngString")
        neg("com.tencent.mobileqq", SDCARD, "tencent/MobileQQ/shortvideo")
        pos("com.tencent.mobileqq", SDCARD, "tencent/MobileQQ/shortvideo/$rngString")
        neg("com.tencent.mobileqq", SDCARD, "Tencent/MobileQQ/chatpic")
        pos("com.tencent.mobileqq", SDCARD, "Tencent/MobileQQ/chatpic/$rngString")
        neg("com.tencent.mobileqq", SDCARD, "Tencent/MobileQQ/shortvideo")
        pos("com.tencent.mobileqq", SDCARD, "Tencent/MobileQQ/shortvideo/$rngString")
        neg("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/MobileQQ/chatpic")
        pos("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/MobileQQ/chatpic/$rngString")
        neg("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/MobileQQ/shortvideo")
        pos("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/MobileQQ/shortvideo/$rngString")

        neg("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/MobileQQ/1234567890/ptt")
        pos("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/MobileQQ/1234567890/ptt/$rngString")

        neg("com.tencent.mobileqq", SDCARD, "tencent/MobileQQ/1234567890/ptt")
        pos("com.tencent.mobileqq", SDCARD, "tencent/MobileQQ/1234567890/ptt/$rngString")

        neg("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/MobileQQ/1234567890/ptt")
        pos("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/MobileQQ/1234567890/ptt/$rngString")

        confirm(create())
    }
}