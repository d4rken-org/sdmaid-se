package eu.darken.sdmse.common.ipc

import eu.darken.sdmse.common.debug.Bugs
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.instanceOf
import okio.IOException
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class ExceptionPropagationTest : BaseTest(), IpcHostModule, IpcClientModule {

    @Test
    fun `propagate exception with stacktrace`() {
        UnsupportedOperationException(
            """
                java.io.IOException: Does not exist or can't be read <-> /storage/1F67-A3A5/.android_secure
                
                #STACK#:rO0ABXVyAB5bTGphdmEubGFuZy5TdGFja1RyYWNlRWxlbWVudDsCRio8PP0iOQIAAHhwAAAABXNyABtqYXZhLmxhbmcuU3RhY2tUcmFjZUVsZW1lbnRhCcWaJjbdhQIABEkACmxpbmVOdW1iZXJMAA5kZWNsYXJpbmdDbGFzc3QAEkxqYXZhL2xhbmcvU3RyaW5nO0wACGZpbGVOYW1lcQB+AANMAAptZXRob2ROYW1lcQB+AAN4cAAAAId0ACJjb20uYW5kcm9pZC5iaWxsaW5nY2xpZW50LmFwaS56emRpcHQADXBlcmZvcm1Mb29rdXBzcQB+AAIAAAB1dAAyZXUuZGFya2VuLnNkbXNlLmNvbW1vbi5maWxlcy5sb2NhbC5pcGMuRmlsZU9wc0hvc3RwdAARbG9va3VwRmlsZXNTdHJlYW1zcQB+AAIAAASTdAA9ZXUuZGFya2VuLnNkbXNlLmNvbW1vbi5maWxlcy5sb2NhbC5pcGMuRmlsZU9wc0Nvbm5lY3Rpb24kU3R1YnB0AApvblRyYW5zYWN0c3EAfgACAAAD/XQAEWFuZHJvaWQub3MuQmluZGVydAALQmluZGVyLmphdmF0ABRleGVjVHJhbnNhY3RJbnRlcm5hbHNxAH4AAgAAA+J0ABFhbmRyb2lkLm9zLkJpbmRlcnQAC0JpbmRlci5qYXZhdAAMZXhlY1RyYW5zYWN0
            """.trimIndent()
        ).unwrapPropagation().apply {
            this shouldBe instanceOf<IOException>()
            message shouldBe "Does not exist or can't be read <-> /storage/1F67-A3A5/.android_secure"
            stackTraceToString().lines().take(5).joinToString("\n").trimIndent() shouldBe """
                java.io.IOException: Does not exist or can't be read <-> /storage/1F67-A3A5/.android_secure
                	at com.android.billingclient.api.zzdi.performLookup(Unknown Source)
                	at eu.darken.sdmse.common.files.local.ipc.FileOpsHost.lookupFilesStream(Unknown Source)
                	at eu.darken.sdmse.common.files.local.ipc.FileOpsConnection${'$'}Stub.onTransact(Unknown Source)
                	at eu.darken.sdmse.common.ipc.ExceptionPropagationTest.propagate exception with stacktrace(ExceptionPropagationTest.kt:16)
            """.trimIndent()
        }
    }

    @Test
    fun `propagate exception without stacktrace`() {
        UnsupportedOperationException(
            """
                java.io.IOException: Does not exist or can't be read <-> /storage/1F67-A3A5/.android_secure
            """.trimIndent()
        ).unwrapPropagation().apply {
            this shouldBe instanceOf<IOException>()
            message shouldBe "Does not exist or can't be read <-> /storage/1F67-A3A5/.android_secure"
        }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun setup() {
            Bugs.isDebug = true
        }

        @JvmStatic
        @AfterAll
        fun teardown() {
            Bugs.isDebug = false
        }
    }

}