package eu.darken.sdmse.automation.core

import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.toPkgId
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2

class LeaveCancelGuardTest : BaseTest() {

    private val home = setOf("com.android.tv.launcher", "com.google.android.tvlauncher")
    private val launcher: Pkg.Id = "com.android.tv.launcher".toPkgId()
    private val settings: Pkg.Id = "com.android.tv.settings".toPkgId()
    private val grace = 1000L

    @Test
    fun `home foreground sustained emits once`() = runTest2 {
        leaveSignals(flowOf<Pkg.Id?>(launcher), home, grace).toList() shouldHaveSize 1
    }

    @Test
    fun `only non-home foreground never emits`() = runTest2 {
        leaveSignals(flowOf<Pkg.Id?>(settings, settings), home, grace).toList().shouldBeEmpty()
    }

    @Test
    fun `null foreground is ignored, a later home still emits`() = runTest2 {
        leaveSignals(flowOf<Pkg.Id?>(null, settings, launcher), home, grace).toList() shouldHaveSize 1
    }

    @Test
    fun `home then engine relaunching settings still emits (latched)`() = runTest2 {
        // After the user presses Home the engine may re-launch Settings; we still honor the Home press.
        leaveSignals(flowOf<Pkg.Id?>(launcher, settings), home, grace).toList() shouldHaveSize 1
    }

    @Test
    fun `does not emit before the grace window elapses`() = runTest2 {
        val results = mutableListOf<Unit>()
        val job = leaveSignals(flow { emit(launcher); awaitCancellation() }, home, grace)
            .onEach { results.add(it) }
            .launchIn(backgroundScope)

        advanceTimeBy(grace - 1)
        runCurrent()
        results.shouldBeEmpty()

        advanceTimeBy(2)
        runCurrent()
        results shouldHaveSize 1

        job.cancel()
    }
}
