package eu.darken.sdmse.automation.core

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import java.io.IOException

class AcsConsentTest : BaseTest() {

    @Test
    fun `current is null and await suspends until the first emission`() = runTest2 {
        val source = MutableSharedFlow<Boolean?>()
        val consent = AcsConsent(source, backgroundScope)
        runCurrent()

        consent.current shouldBe null

        val awaited = async { consent.await() }
        runCurrent()
        awaited.isCompleted shouldBe false

        source.emit(true)
        runCurrent()

        awaited.await() shouldBe true
        consent.current shouldBe true
    }

    @Test
    fun `a stored null is not mistaken for not-loaded-yet`() = runTest2 {
        val consent = AcsConsent(flowOf(null), backgroundScope)

        consent.await() shouldBe null
        consent.current shouldBe null
    }

    @Test
    fun `a failing source is retried and recovers`() = runTest2 {
        var attempts = 0
        val source = flow {
            attempts++
            if (attempts == 1) throw IOException("Read failed")
            emit(true)
        }
        val consent = AcsConsent(source, backgroundScope)

        advanceTimeBy(1001)
        runCurrent()

        consent.current shouldBe true
        consent.await() shouldBe true
        attempts shouldBe 2
    }

    @Test
    fun `later emissions are propagated`() = runTest2 {
        val source = MutableStateFlow<Boolean?>(true)
        val consent = AcsConsent(source, backgroundScope)

        consent.await() shouldBe true

        source.value = false
        runCurrent()

        consent.current shouldBe false
    }
}
