package eu.darken.sdmse.appcleaner.core.automation.specs.aosp

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

/**
 * Covers what AOSPSpecs does when the app's storage page is up but no clickable "Clear cache"
 * exists in the accessibility tree.
 *
 * Motivating report: a Motorola Hello UI device on Android 17 where four system apps whose caches
 * were already empty each cost 30s. Settings printed "Cache / 0 byte" and rendered the clear-cache
 * row as a disabled, zero-height LinearLayout with no text and no content-desc, so it could never
 * be matched and the step just ran out its timeout.
 */
class ClearCacheButtonVerdictTest : BaseTest() {

    @Test
    fun `an empty cache means there is nothing left to click`() {
        noButtonVerdict(cacheSize = 0L, isSystemApp = true, useDpadFallback = false) shouldBe
            NoButtonVerdict.ALREADY_EMPTY
    }

    @Test
    fun `an empty cache short-circuits before the DPAD fallback too`() {
        noButtonVerdict(cacheSize = 0L, isSystemApp = false, useDpadFallback = true) shouldBe
            NoButtonVerdict.ALREADY_EMPTY
    }

    @Test
    fun `an unreadable cache size is never treated as non-empty`() {
        noButtonVerdict(cacheSize = null, isSystemApp = true, useDpadFallback = false) shouldBe
            NoButtonVerdict.KEEP_TRYING
    }

    @Test
    fun `a system app with cache and no button is unclearable`() {
        noButtonVerdict(cacheSize = 143360L, isSystemApp = true, useDpadFallback = false) shouldBe
            NoButtonVerdict.NO_BUTTON
    }

    @Test
    fun `a normal app with cache and no button keeps polling`() {
        noButtonVerdict(cacheSize = 143360L, isSystemApp = false, useDpadFallback = false) shouldBe
            NoButtonVerdict.KEEP_TRYING
    }

    @Test
    fun `a missing button proves nothing while DPAD is still in play`() {
        noButtonVerdict(cacheSize = 143360L, isSystemApp = true, useDpadFallback = true) shouldBe
            NoButtonVerdict.KEEP_TRYING
    }

    @Test
    fun `a terminal verdict is held back until it repeats`() {
        val confirmer = VerdictConfirmer()

        confirmer.confirm(NoButtonVerdict.ALREADY_EMPTY) shouldBe NoButtonVerdict.KEEP_TRYING
        confirmer.confirm(NoButtonVerdict.ALREADY_EMPTY) shouldBe NoButtonVerdict.ALREADY_EMPTY
    }

    @Test
    fun `a verdict that does not repeat is discarded`() {
        val confirmer = VerdictConfirmer()

        // The screen was still settling: first pass saw no cache row, second saw an empty one.
        confirmer.confirm(NoButtonVerdict.ALREADY_EMPTY) shouldBe NoButtonVerdict.KEEP_TRYING
        confirmer.confirm(NoButtonVerdict.KEEP_TRYING) shouldBe NoButtonVerdict.KEEP_TRYING
        confirmer.confirm(NoButtonVerdict.ALREADY_EMPTY) shouldBe NoButtonVerdict.KEEP_TRYING
        confirmer.confirm(NoButtonVerdict.ALREADY_EMPTY) shouldBe NoButtonVerdict.ALREADY_EMPTY
    }

    @Test
    fun `two different terminal verdicts do not confirm each other`() {
        val confirmer = VerdictConfirmer()

        confirmer.confirm(NoButtonVerdict.ALREADY_EMPTY) shouldBe NoButtonVerdict.KEEP_TRYING
        confirmer.confirm(NoButtonVerdict.NO_BUTTON) shouldBe NoButtonVerdict.KEEP_TRYING
        confirmer.confirm(NoButtonVerdict.NO_BUTTON) shouldBe NoButtonVerdict.NO_BUTTON
    }

    @Test
    fun `keep trying never confirms itself into an action`() {
        val confirmer = VerdictConfirmer()

        repeat(5) { confirmer.confirm(NoButtonVerdict.KEEP_TRYING) shouldBe NoButtonVerdict.KEEP_TRYING }
    }
}
