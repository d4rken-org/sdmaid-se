package eu.darken.sdmse.main.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.io.IOException

class PartialResultExceptionTest : BaseTest() {

    private val result: SDMTool.Task.Result = mockk(relaxed = true)

    @Test
    fun `a failure and the work that survived it are both kept`() {
        val boom = IOException("Disk on fire")

        val wrapper = PartialResultException(boom, result)

        wrapper.cause shouldBe boom
        wrapper.partialResult shouldBe result
    }

    @Test
    fun `a cancellation cannot be wrapped`() {
        // A cancelled task must stay cancelled, not become a partial success.
        shouldThrow<IllegalArgumentException> {
            PartialResultException(CancellationException("cancelled"), result)
        }
    }
}
