package eu.darken.sdmse.main.core.taskmanager

import eu.darken.sdmse.common.error.HasLocalizedError
import eu.darken.sdmse.main.core.SDMTool
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.types.beInstanceOf
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import kotlin.time.Duration.Companion.hours

class TaskTimeoutExceptionTest : BaseTest() {

    @Test
    fun `is not a CancellationException`() {
        val exception = TaskTimeoutException(SDMTool.Type.APPCLEANER, 4.hours)
        exception shouldNot beInstanceOf<kotlinx.coroutines.CancellationException>()
    }

    @Test
    fun `implements HasLocalizedError`() {
        val exception = TaskTimeoutException(SDMTool.Type.APPCLEANER, 4.hours)
        exception.shouldBeInstanceOf<HasLocalizedError>()
        val localized = exception.getLocalizedError()
        localized.throwable shouldBe exception
    }

    @Test
    fun `message contains tool type and timeout`() {
        val exception = TaskTimeoutException(SDMTool.Type.DEDUPLICATOR, 6.hours)
        exception.message shouldBe "Task for DEDUPLICATOR timed out after 6h"
    }

    @Test
    fun `stores tool type and timeout`() {
        val exception = TaskTimeoutException(SDMTool.Type.CORPSEFINDER, 4.hours)
        exception.toolType shouldBe SDMTool.Type.CORPSEFINDER
        exception.timeout shouldBe 4.hours
    }
}
