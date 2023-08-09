package eu.darken.sdmse.common.coroutine

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.util.UUID

class SuspendingLazyTest : BaseTest() {

    @Test
    fun `value is cached`() = runTest {
        val suspendingLazy = SuspendingLazy { UUID.randomUUID().toString() }
        suspendingLazy.value() shouldBe suspendingLazy.value()
    }
}
