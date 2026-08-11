package eu.darken.sdmse.common.debug.exit

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class ExitInfoLoggerTest : BaseTest() {

    @Test fun `known reason codes map to their constant names`() {
        exitReasonLabel(0) shouldBe "REASON_UNKNOWN"
        exitReasonLabel(3) shouldBe "REASON_LOW_MEMORY"
        exitReasonLabel(4) shouldBe "REASON_CRASH"
        exitReasonLabel(5) shouldBe "REASON_CRASH_NATIVE"
        exitReasonLabel(6) shouldBe "REASON_ANR"
        exitReasonLabel(9) shouldBe "REASON_EXCESSIVE_RESOURCE_USAGE"
        exitReasonLabel(14) shouldBe "REASON_FREEZER"
    }

    @Test fun `an unknown reason code keeps its raw value`() {
        exitReasonLabel(99) shouldBe "UNKNOWN(99)"
        exitReasonLabel(-1) shouldBe "UNKNOWN(-1)"
    }
}
