package eu.darken.sdmse.common.compose.tour

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class NoOpGuidedTourAccessTest : BaseTest() {

    @Test
    fun `shouldStart is always false`() = runTest {
        NoOpGuidedTourAccess.shouldStart(mockk(relaxed = true)) shouldBe false
    }

    @Test
    fun `session stays null across start and skip`() = runTest {
        val definition = mockk<TourDefinition>(relaxed = true)
        NoOpGuidedTourAccess.session.value shouldBe null
        NoOpGuidedTourAccess.start(definition)
        NoOpGuidedTourAccess.skipForNow()
        NoOpGuidedTourAccess.session.value shouldBe null
    }
}
