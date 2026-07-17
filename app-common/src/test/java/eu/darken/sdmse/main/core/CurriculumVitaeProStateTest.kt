package eu.darken.sdmse.main.core

import eu.darken.sdmse.main.core.CurriculumVitae.ProState
import eu.darken.sdmse.main.core.CurriculumVitae.ProTransition
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class CurriculumVitaeProStateTest : BaseTest() {

    @Test fun `grace only engages coming from a confirmed purchase`() {
        CurriculumVitae.proTransitionOf(ProState.PURCHASED, ProState.GRACE) shouldBe ProTransition.GRACE_ENGAGED

        // A launch that settles straight into grace (or a baseline) is not a new engagement.
        CurriculumVitae.proTransitionOf(null, ProState.GRACE) shouldBe null
        CurriculumVitae.proTransitionOf(ProState.FREE, ProState.GRACE) shouldBe null
        CurriculumVitae.proTransitionOf(ProState.GRACE, ProState.GRACE) shouldBe null
    }

    @Test fun `pro is lost when a pro-ish state drops to free`() {
        CurriculumVitae.proTransitionOf(ProState.PURCHASED, ProState.FREE) shouldBe ProTransition.PRO_LOST
        CurriculumVitae.proTransitionOf(ProState.GRACE, ProState.FREE) shouldBe ProTransition.PRO_LOST

        CurriculumVitae.proTransitionOf(null, ProState.FREE) shouldBe null
        CurriculumVitae.proTransitionOf(ProState.FREE, ProState.FREE) shouldBe null
    }

    @Test fun `recovering pro is never a counted transition`() {
        CurriculumVitae.proTransitionOf(null, ProState.PURCHASED) shouldBe null
        CurriculumVitae.proTransitionOf(ProState.GRACE, ProState.PURCHASED) shouldBe null
        CurriculumVitae.proTransitionOf(ProState.FREE, ProState.PURCHASED) shouldBe null
        CurriculumVitae.proTransitionOf(ProState.PURCHASED, ProState.PURCHASED) shouldBe null
    }

    @Test fun `stored state parsing tolerates blank, corrupt and future values`() {
        CurriculumVitae.parseProState(null) shouldBe null
        CurriculumVitae.parseProState("") shouldBe null
        CurriculumVitae.parseProState("garbage") shouldBe null
        CurriculumVitae.parseProState("PURCHASED_V2") shouldBe null
        CurriculumVitae.parseProState("PURCHASED") shouldBe ProState.PURCHASED
        CurriculumVitae.parseProState("GRACE") shouldBe ProState.GRACE
        CurriculumVitae.parseProState("FREE") shouldBe ProState.FREE
    }
}
