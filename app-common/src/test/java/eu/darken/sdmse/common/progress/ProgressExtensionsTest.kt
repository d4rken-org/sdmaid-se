package eu.darken.sdmse.common.progress

import android.content.Context
import eu.darken.sdmse.common.ca.toCaString
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class ProgressExtensionsTest : BaseTest() {

    private val context = mockk<Context>(relaxed = true)

    /** Captures every intermediate [Progress.Data], so a torn two-step write shows up as two emissions. */
    private class RecordingClient : Progress.Client {
        val emissions = mutableListOf<Progress.Data?>()
        private var current: Progress.Data? = null

        override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
            current = update(current)
            emissions.add(current)
        }
    }

    @Test
    fun `the atomic CaString overload writes label and payload in one emission`() {
        val payload = Any()
        val client = RecordingClient()

        client.updateProgressSecondary("Some app".toCaString(), extra = payload)

        client.emissions.size shouldBe 1
        client.emissions.single()!!.secondary.get(context) shouldBe "Some app"
        client.emissions.single()!!.extra shouldBe payload
    }

    @Test
    fun `the atomic String overload writes label and payload in one emission`() {
        val payload = Any()
        val client = RecordingClient()

        client.updateProgressSecondary("Some app", extra = payload)

        client.emissions.size shouldBe 1
        client.emissions.single()!!.secondary.get(context) shouldBe "Some app"
        client.emissions.single()!!.extra shouldBe payload
    }

    @Test
    fun `the atomic overloads can clear the payload`() {
        val client = RecordingClient()

        client.updateProgressSecondary("Some app".toCaString(), extra = Any())
        client.updateProgressSecondary("Other app".toCaString(), extra = null)

        client.emissions.last()!!.extra shouldBe null
    }

    @Test
    fun `the String overload clears a previous payload`() {
        val client = RecordingClient()

        client.updateProgressSecondary("Some app".toCaString(), extra = Any())
        client.updateProgressSecondary("Scanning media files")

        client.emissions.last()!!.secondary.get(context) shouldBe "Scanning media files"
        client.emissions.last()!!.extra shouldBe null
    }

    @Test
    fun `the resolver overload clears a previous payload`() {
        val client = RecordingClient()

        client.updateProgressSecondary("Some app".toCaString(), extra = Any())
        client.updateProgressSecondary { "Scanning media files" }

        client.emissions.last()!!.secondary.get(context) shouldBe "Scanning media files"
        client.emissions.last()!!.extra shouldBe null
    }

    @Test
    fun `the CaString overload clears a previous payload`() {
        val client = RecordingClient()

        client.updateProgressSecondary("Some app".toCaString(), extra = Any())
        client.updateProgressSecondary("Scanning media files".toCaString())

        client.emissions.last()!!.secondary.get(context) shouldBe "Scanning media files"
        client.emissions.last()!!.extra shouldBe null
    }

    @Test
    fun `the string resource overload clears a previous payload`() {
        val client = RecordingClient()

        client.updateProgressSecondary("Some app".toCaString(), extra = Any())
        client.updateProgressSecondary(42, "arg")

        client.emissions.last()!!.extra shouldBe null
    }

    @Test
    fun `updating the phase label leaves the payload untouched`() {
        val payload = Any()
        val client = RecordingClient()

        client.updateProgressSecondary("Some app".toCaString(), extra = payload)
        client.updateProgressPrimary("Scanning apps")

        client.emissions.last()!!.primary.get(context) shouldBe "Scanning apps"
        client.emissions.last()!!.extra shouldBe payload
    }
}
