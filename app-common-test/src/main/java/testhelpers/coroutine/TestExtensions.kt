package testhelpers.coroutine

import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun runTest2(
    autoCancel: Boolean = false,
    context: CoroutineContext = EmptyCoroutineContext,
    expectedError: KClass<out Throwable>? = null,
    timeout: Duration = 60.seconds,
    testBody: suspend TestScope.() -> Unit
) {
    try {
        val scope = TestScope(context = context)
        try {
            scope.runTest(
                timeout = timeout
            ) {
                testBody()
                if (autoCancel) scope.cancel("autoCancel")
            }
        } catch (e: Throwable) {
            val isExpected = expectedError?.isInstance(e) ?: false
            if (!isExpected) throw e
        }
    } catch (e: CancellationException) {
        if (e.message == "autoCancel" && autoCancel) {
            log("test") { "Test was auto-cancelled ${e.asLog()}" }
        } else {
            throw e
        }
    }
}

