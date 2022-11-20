package testhelpers.coroutine

import eu.darken.sdmse.common.debug.logging.asLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.reflect.KClass

fun runTest2(
    autoCancel: Boolean = false,
    context: CoroutineContext = EmptyCoroutineContext,
    expectedError: KClass<out Throwable>? = null,
    testBody: suspend TestScope.() -> Unit
) {
    try {
        val scope = TestScope(context = context)
        try {
            scope.runTest {
                testBody()
                if (autoCancel) scope.cancel("autoCancel")
            }
        } catch (e: Throwable) {
            val isExpected = expectedError?.isInstance(e) ?: false
            if (!isExpected) throw e
        }
    } catch (e: CancellationException) {
        if (e.message == "autoCancel" && autoCancel) {
            io.kotest.mpp.log { "Test was auto-cancelled ${e.asLog()}" }
        } else {
            throw e
        }
    }
}

