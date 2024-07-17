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
                eu.darken.sdmse.common.files.ReadException: Does not exist or can't be read <-> /storage/1F67-A3A5/.android_secure
                
                #STACK#:rO0ABXVyAB5bTGphdmEubGFuZy5TdGFja1RyYWNlRWxlbWVudDsCRio8PP0iOQIAAHhwAAAABXNyABtqYXZhLmxhbmcuU3RhY2tUcmFjZUVsZW1lbnRhCcWaJjbdhQIABEkACmxpbmVOdW1iZXJMAA5kZWNsYXJpbmdDbGFzc3QAEkxqYXZhL2xhbmcvU3RyaW5nO0wACGZpbGVOYW1lcQB+AANMAAptZXRob2ROYW1lcQB+AAN4cAAAAId0ABdjb2lsLmRlY29kZS5EZWNvZGVVdGlsc3B0AA1wZXJmb3JtTG9va3Vwc3EAfgACAAAAdXQAMmV1LmRhcmtlbi5zZG1zZS5jb21tb24uZmlsZXMubG9jYWwuaXBjLkZpbGVPcHNIb3N0cHQAEWxvb2t1cEZpbGVzU3RyZWFtc3EAfgACAAAEn3QAPWV1LmRhcmtlbi5zZG1zZS5jb21tb24uZmlsZXMubG9jYWwuaXBjLkZpbGVPcHNDb25uZWN0aW9uJFN0dWJwdAAKb25UcmFuc2FjdHNxAH4AAgAAA/10ABFhbmRyb2lkLm9zLkJpbmRlcnQAC0JpbmRlci5qYXZhdAAUZXhlY1RyYW5zYWN0SW50ZXJuYWxzcQB+AAIAAAPidAARYW5kcm9pZC5vcy5CaW5kZXJ0AAtCaW5kZXIuamF2YXQADGV4ZWNUcmFuc2FjdA==
            """.trimIndent()
        ).unwrapPropagation().apply {
            this shouldBe instanceOf<IOException>()
            message shouldBe "eu.darken.sdmse.common.files.ReadException: Does not exist or can't be read <-> /storage/1F67-A3A5/.android_secure"
            stackTraceToString().lines().take(5).joinToString("\n").trimIndent() shouldBe """
                eu.darken.sdmse.common.ipc.UnwrappedIPCException: eu.darken.sdmse.common.files.ReadException: Does not exist or can't be read <-> /storage/1F67-A3A5/.android_secure
                	at coil.decode.DecodeUtils.performLookup(Unknown Source)
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
            message shouldBe "java.io.IOException: Does not exist or can't be read <-> /storage/1F67-A3A5/.android_secure"
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