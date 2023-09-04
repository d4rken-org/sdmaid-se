package eu.darken.sdmse.main.core.motd

import eu.darken.sdmse.common.http.HttpModule
import eu.darken.sdmse.common.serialization.SerializationAppModule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import java.io.File
import java.util.Locale

class MotdDataTest : BaseTest() {

    private lateinit var webServer: MockWebServer
    private lateinit var motdEndpoint: MotdEndpoint
    private val mockUrl: String
        get() = webServer.url("/").toString()

    @BeforeEach
    fun setup() {
        webServer = MockWebServer()
        motdEndpoint = MotdEndpoint(
            dispatcherProvider = TestDispatcherProvider(),
            baseHttpClient = HttpModule().baseHttpClient(),
            baseMoshi = SerializationAppModule().moshi(),
        )
        motdEndpoint.endpointUrlOverride = mockUrl

        File(".").listFiles()
    }

    @AfterEach
    fun teardown() {
        webServer.close()
    }

    private fun mockListingResponse(flavor: String, type: String, locale: Locale) {
        val response = """
            [
                {
                    "name": "motd-${locale.language}.json",
                    "download_url": "${mockUrl}d4rken-org/sdmaid-se/main/motd/$flavor/$type/motd-${locale.language}.json",
                    "type": "file"
                }
            ]
        """.trimIndent()
        webServer.enqueue(MockResponse().setBody(response))
    }

    private suspend fun checkMotds(
        flavor: String,
        type: String,
    ) {
        File("../motd/$flavor/$type").listFiles()?.forEach { motdFile ->
            mockListingResponse(flavor, type, Locale.ENGLISH)
            webServer.enqueue(MockResponse().setBody(motdFile.readText()))
            motdEndpoint.getMotd(Locale.ENGLISH)!!.apply {
                allowTranslation shouldBe false
                motd.primaryLink?.startsWith("https://")
            }

            val missingLocale = Locale.forLanguageTag("aa-aa")
            mockListingResponse(flavor, type, missingLocale)
            webServer.enqueue(MockResponse().setBody(motdFile.readText()))
            motdEndpoint.getMotd(missingLocale)!!.allowTranslation shouldBe true
        }
    }

    @Test
    fun `foss dev`() = runTest {
        withClue("There should always be a test file for dev") {
            File("../motd/foss/dev").list().isNullOrEmpty() shouldBe false
        }
        checkMotds("foss", "dev")
    }

    @Test
    fun `foss beta`() = runTest {
        checkMotds("foss", "beta")
    }

    @Test
    fun `foss release`() = runTest {
        checkMotds("foss", "release")
    }

    @Test
    fun `gplay dev`() = runTest {
        withClue("There should always be a test file for dev") {
            File("../motd/gplay/dev").list().isNullOrEmpty() shouldBe false
        }
        checkMotds("gplay", "dev")
    }

    @Test
    fun `gplay beta`() = runTest {
        checkMotds("gplay", "beta")
    }

    @Test
    fun `gplay release`() = runTest {
        checkMotds("gplay", "release")
    }

    @Test
    fun `errors are rethrown`() = runTest {
        webServer.enqueue(MockResponse().setResponseCode(500))
        shouldThrow<Exception> {
            motdEndpoint.getMotd(Locale.ENGLISH)
        }
    }

    @Test
    fun `404 returns null`() = runTest {
        webServer.enqueue(MockResponse().setResponseCode(404))
        motdEndpoint.getMotd(Locale.ENGLISH) shouldBe null
    }
}