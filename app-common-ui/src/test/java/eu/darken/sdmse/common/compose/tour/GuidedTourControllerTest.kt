package eu.darken.sdmse.common.compose.tour

import androidx.navigation3.runtime.NavKey
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.main.core.tour.TourPreferences
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import testhelpers.BaseTest

@ExtendWith(MockKExtension::class)
class GuidedTourControllerTest : BaseTest() {

    @MockK lateinit var generalSettings: GeneralSettings

    private lateinit var prefsFlow: MutableStateFlow<TourPreferences>
    private lateinit var prefsValue: DataStoreValue<TourPreferences>
    private lateinit var enabledFlow: MutableStateFlow<Boolean>
    private lateinit var enabledValue: DataStoreValue<Boolean>

    private val basicDefinition = TourDefinition(
        id = TourId("test.basic"),
        steps = listOf(
            TourStep(stepId = "a", body = "A".toCaString()),
            TourStep(stepId = "b", body = "B".toCaString()),
            TourStep(stepId = "c", body = "C".toCaString()),
        ),
        clickProtection = true,
    )

    private val unprotectedDefinition = basicDefinition.copy(
        id = TourId("test.unprotected"),
        clickProtection = false,
    )

    @BeforeEach
    fun setup() {
        prefsFlow = MutableStateFlow(TourPreferences())
        prefsValue = mockk {
            every { flow } returns prefsFlow
            coEvery { update(any<(TourPreferences) -> TourPreferences?>()) } coAnswers {
                @Suppress("UNCHECKED_CAST")
                val fn = firstArg<(TourPreferences) -> TourPreferences?>()
                val old = prefsFlow.value
                val new = fn(old) ?: old
                prefsFlow.value = new
                DataStoreValue.Updated(old, new)
            }
        }
        every { generalSettings.tourPreferences } returns prefsValue

        enabledFlow = MutableStateFlow(true)
        enabledValue = mockk {
            every { flow } returns enabledFlow
            coEvery { update(any<(Boolean) -> Boolean?>()) } coAnswers {
                @Suppress("UNCHECKED_CAST")
                val fn = firstArg<(Boolean) -> Boolean?>()
                val old = enabledFlow.value
                val new = fn(old) ?: old
                enabledFlow.value = new
                DataStoreValue.Updated(old, new)
            }
        }
        every { generalSettings.isGuidedToursEnabled } returns enabledValue
    }

    private fun TestScope.controller(): GuidedTourController = GuidedTourController(
        generalSettings = generalSettings,
        scope = this,
    )

    @Test
    fun `shouldStart is true on a fresh tour`() = runTest {
        controller().shouldStart(basicDefinition) shouldBe true
    }

    @Test
    fun `shouldStart is false when completed`() = runTest {
        prefsFlow.value = TourPreferences(completed = setOf(basicDefinition.id.raw))
        controller().shouldStart(basicDefinition) shouldBe false
    }

    @Test
    fun `shouldStart is false when dismissed`() = runTest {
        prefsFlow.value = TourPreferences(dismissed = setOf(basicDefinition.id.raw))
        controller().shouldStart(basicDefinition) shouldBe false
    }

    @Test
    fun `shouldStart is false when a session is active`() = runTest {
        val ctrl = controller()
        ctrl.start(basicDefinition)
        ctrl.shouldStart(basicDefinition) shouldBe false
    }

    @Test
    fun `shouldStart is false when guided tours are globally disabled`() = runTest {
        enabledFlow.value = false
        controller().shouldStart(basicDefinition) shouldBe false
    }

    @Test
    fun `start no-ops when guided tours are globally disabled`() = runTest {
        enabledFlow.value = false
        val ctrl = controller()
        ctrl.start(basicDefinition)
        ctrl.session.value shouldBe null
    }

    @Test
    fun `reset re-enables guided tours`() = runTest {
        enabledFlow.value = false
        controller().reset()
        enabledFlow.value shouldBe true
    }

    @Test
    fun `disableAllTours clears the session and persists the flag off`() = runTest {
        val ctrl = controller()
        ctrl.start(basicDefinition)
        ctrl.disableAllTours()
        ctrl.session.value shouldBe null
        enabledFlow.value shouldBe false
    }

    @Test
    fun `disableAllTours leaves per-tour preferences untouched`() = runTest {
        prefsFlow.value = TourPreferences(completed = setOf("x"), dismissed = setOf("y"))
        val ctrl = controller()
        ctrl.start(basicDefinition)
        ctrl.disableAllTours()
        // The global flag dominates shouldStart(); per-tour state is reset() territory, not this.
        prefsFlow.value shouldBe TourPreferences(completed = setOf("x"), dismissed = setOf("y"))
    }

    @Test
    fun `disableAllTours with no active session still disables`() = runTest {
        val ctrl = controller()
        ctrl.disableAllTours()
        ctrl.session.value shouldBe null
        enabledFlow.value shouldBe false
    }

    @Test
    fun `disableAllTours suppresses all tours until reset re-enables them`() = runTest {
        val ctrl = controller()
        ctrl.disableAllTours()
        ctrl.shouldStart(basicDefinition) shouldBe false
        ctrl.reset()
        ctrl.shouldStart(basicDefinition) shouldBe true
    }

    @Test
    fun `start no-ops when blocked by completed prefs`() = runTest {
        prefsFlow.value = TourPreferences(completed = setOf(basicDefinition.id.raw))
        val ctrl = controller()
        ctrl.start(basicDefinition)
        ctrl.session.value shouldBe null
    }

    @Test
    fun `next advances stepIndex`() = runTest {
        val ctrl = controller()
        ctrl.start(basicDefinition)
        ctrl.session.value!!.stepIndex shouldBe 0
        ctrl.next()
        ctrl.session.value!!.stepIndex shouldBe 1
    }

    @Test
    fun `next from last step completes tour and persists completed`() = runTest {
        val ctrl = controller()
        ctrl.start(basicDefinition)
        ctrl.markStepRendered(basicDefinition.id) // host showed a step — a real walkthrough, persists
        ctrl.next()
        ctrl.next()
        ctrl.next() // last → complete
        ctrl.session.value shouldBe null
        prefsFlow.value.completed shouldBe setOf(basicDefinition.id.raw)
        prefsFlow.value.dismissed shouldBe emptySet()
    }

    @Test
    fun `successful completion invokes onComplete after persistence and session clearing`() = runTest {
        lateinit var ctrl: GuidedTourController
        var completionCalls = 0
        val def = basicDefinition.copy(
            id = TourId("test.oncomplete"),
            onComplete = {
                ctrl.session.value shouldBe null
                prefsFlow.value.completed shouldBe setOf("test.oncomplete")
                completionCalls++
            },
        )
        ctrl = controller()
        ctrl.start(def)
        ctrl.markStepRendered(def.id)

        ctrl.next()
        ctrl.next()
        ctrl.next() // Finish on the last step.

        completionCalls shouldBe 1
    }

    @Test
    fun `non-completion exits do not invoke onComplete`() = runTest {
        var completionCalls = 0
        fun definition(id: String) = basicDefinition.copy(
            id = TourId(id),
            onComplete = { completionCalls++ },
        )

        controller().apply {
            start(definition("test.oncomplete.skip"))
            skipForNow()
        }
        controller().apply {
            start(definition("test.oncomplete.dismiss"))
            dismissForever()
        }
        controller().apply {
            start(definition("test.oncomplete.disable"))
            disableAllTours()
        }

        completionCalls shouldBe 0
    }

    @Test
    fun `no-render completion fallback does not invoke onComplete`() = runTest {
        var completionCalls = 0
        val def = basicDefinition.copy(
            id = TourId("test.oncomplete.no-render"),
            onComplete = { completionCalls++ },
        )
        val ctrl = controller()
        ctrl.start(def)

        ctrl.complete()

        completionCalls shouldBe 0
    }

    @Test
    fun `next to end without any rendered step skips instead of persisting`() = runTest {
        val ctrl = controller()
        ctrl.start(basicDefinition) // no markStepRendered: every step grace-skipped, nothing shown
        ctrl.next()
        ctrl.next()
        ctrl.next() // last → would complete, but nothing rendered → skip-for-now
        ctrl.session.value shouldBe null
        prefsFlow.value.completed shouldBe emptySet()
        prefsFlow.value.dismissed shouldBe emptySet()
        // Suppressed in-memory for this process, but eligible again after an app restart.
        ctrl.shouldStart(basicDefinition) shouldBe false
        controller().shouldStart(basicDefinition) shouldBe true
    }

    @Test
    fun `previous decrements stepIndex`() = runTest {
        val ctrl = controller()
        ctrl.start(basicDefinition)
        ctrl.next()
        ctrl.session.value!!.stepIndex shouldBe 1
        ctrl.previous()
        ctrl.session.value!!.stepIndex shouldBe 0
    }

    @Test
    fun `previous at first step is a no-op`() = runTest {
        val ctrl = controller()
        ctrl.start(basicDefinition)
        ctrl.session.value!!.stepIndex shouldBe 0
        ctrl.previous()
        ctrl.session.value!!.stepIndex shouldBe 0
    }

    @Test
    fun `previous without a session is a no-op`() = runTest {
        val ctrl = controller()
        ctrl.previous()
        ctrl.session.value shouldBe null
    }

    @Test
    fun `dismissForever persists to dismissed and clears session`() = runTest {
        val ctrl = controller()
        ctrl.start(basicDefinition)
        ctrl.dismissForever()
        ctrl.session.value shouldBe null
        prefsFlow.value.dismissed shouldBe setOf(basicDefinition.id.raw)
        prefsFlow.value.completed shouldBe emptySet()
    }

    @Test
    fun `skipForNow clears the session without persistence`() = runTest {
        val ctrl = controller()
        ctrl.start(basicDefinition)
        ctrl.skipForNow()
        ctrl.session.value shouldBe null
        prefsFlow.value.dismissed shouldBe emptySet()
        prefsFlow.value.completed shouldBe emptySet()
    }

    @Test
    fun `skipForNow suppresses the tour for the rest of the session without persisting`() = runTest {
        val ctrl = controller()
        ctrl.start(basicDefinition)
        ctrl.skipForNow()
        // No persistence — a freshly constructed controller (simulating an app restart) treats the
        // tour as eligible again.
        prefsFlow.value.dismissed shouldBe emptySet()
        prefsFlow.value.completed shouldBe emptySet()
        controller().shouldStart(basicDefinition) shouldBe true
        // Same controller (current process) keeps the skip in memory.
        ctrl.shouldStart(basicDefinition) shouldBe false
        ctrl.start(basicDefinition)
        ctrl.session.value shouldBe null
    }

    @Test
    fun `reset clears in-memory skip so the tour is eligible again`() = runTest {
        val ctrl = controller()
        ctrl.start(basicDefinition)
        ctrl.skipForNow()
        ctrl.shouldStart(basicDefinition) shouldBe false
        ctrl.reset()
        ctrl.shouldStart(basicDefinition) shouldBe true
    }

    @Test
    fun `complete persists to completed and clears session`() = runTest {
        val ctrl = controller()
        ctrl.start(basicDefinition)
        ctrl.markStepRendered(basicDefinition.id)
        ctrl.complete()
        ctrl.session.value shouldBe null
        prefsFlow.value.completed shouldBe setOf(basicDefinition.id.raw)
    }

    @Test
    fun `complete with no rendered step does not persist and stays eligible after restart`() = runTest {
        val ctrl = controller()
        ctrl.start(basicDefinition)
        ctrl.complete() // nothing ever rendered → must not burn the tour
        ctrl.session.value shouldBe null
        prefsFlow.value.completed shouldBe emptySet()
        prefsFlow.value.dismissed shouldBe emptySet()
        controller().shouldStart(basicDefinition) shouldBe true
    }

    @Test
    fun `markStepRendered ignores a tour id that is not the active session`() = runTest {
        val ctrl = controller()
        ctrl.start(basicDefinition)
        // A late render callback for a different (already-ended) tour must not mark this session.
        ctrl.markStepRendered(TourId("some.other.tour"))
        ctrl.complete()
        prefsFlow.value.completed shouldBe emptySet()
        controller().shouldStart(basicDefinition) shouldBe true
    }

    @Test
    fun `reset clears completed and dismissed sets`() = runTest {
        prefsFlow.value = TourPreferences(
            completed = setOf("a", "b"),
            dismissed = setOf("c"),
        )
        controller().reset()
        prefsFlow.value shouldBe TourPreferences()
    }

    @Test
    fun `route snapshot regression — onRouteChanged before start seeds the start route`() = runTest {
        val ctrl = controller()
        val routeA: NavKey = TestRoute("a")
        val routeB: NavKey = TestRoute("b")
        // Simulate MainActivity emitting the current route BEFORE the dashboard auto-starts the tour.
        ctrl.onRouteChanged(routeA)
        ctrl.start(unprotectedDefinition)
        ctrl.markStepRendered(unprotectedDefinition.id) // a step was shown before navigating away
        ctrl.session.value shouldBe TourSessionAt(unprotectedDefinition, 0)
        // User navigates away → the controller must auto-complete (regression for the stale-seed bug).
        ctrl.onRouteChanged(routeB)
        // onRouteChanged launches complete() into the controller's scope. Let the test scheduler run.
        runCurrentUntilIdle()
        ctrl.session.value shouldBe null
        prefsFlow.value.completed shouldBe setOf(unprotectedDefinition.id.raw)
    }

    @Test
    fun `same route after start is no-op`() = runTest {
        val ctrl = controller()
        val routeA: NavKey = TestRoute("a")
        ctrl.onRouteChanged(routeA)
        ctrl.start(unprotectedDefinition)
        ctrl.onRouteChanged(routeA)
        runCurrentUntilIdle()
        ctrl.session.value!!.stepIndex shouldBe 0
    }

    @Test
    fun `clickProtection true ignores route changes`() = runTest {
        val ctrl = controller()
        val routeA: NavKey = TestRoute("a")
        val routeB: NavKey = TestRoute("b")
        ctrl.onRouteChanged(routeA)
        ctrl.start(basicDefinition) // basicDefinition has clickProtection = true
        ctrl.onRouteChanged(routeB)
        runCurrentUntilIdle()
        ctrl.session.value!!.stepIndex shouldBe 0
        prefsFlow.value.completed shouldBe emptySet()
    }

    @Test
    fun `first-step prepareTarget is awaited before session is published`() = runTest {
        val signal = CompletableDeferred<Unit>()
        val def = TourDefinition(
            id = TourId("test.firstprep"),
            steps = listOf(
                TourStep(
                    stepId = "first",
                    body = "first".toCaString(),
                    prepareTarget = { signal.await() },
                ),
                TourStep(stepId = "second", body = "second".toCaString()),
            ),
        )
        val ctrl = controller()
        val startJob = launch { ctrl.start(def) }
        runCurrentUntilIdle()
        // Session must not be published while prepareTarget is still running.
        ctrl.session.value shouldBe null
        signal.complete(Unit)
        startJob.join()
        ctrl.session.value shouldBe TourSessionAt(def, 0)
    }

    @Test
    fun `next-step prepareTarget runs before stepIndex advances`() = runTest {
        val signal = CompletableDeferred<Unit>()
        val def = TourDefinition(
            id = TourId("test.nextprep"),
            steps = listOf(
                TourStep(stepId = "first", body = "first".toCaString()),
                TourStep(
                    stepId = "second",
                    body = "second".toCaString(),
                    prepareTarget = { signal.await() },
                ),
            ),
        )
        val ctrl = controller()
        ctrl.start(def)
        val advanceJob: Job = launch { ctrl.next() }
        runCurrentUntilIdle()
        // While prepareTarget is suspended, the index must NOT have advanced.
        ctrl.session.value!!.stepIndex shouldBe 0
        signal.complete(Unit)
        advanceJob.join()
        ctrl.session.value!!.stepIndex shouldBe 1
    }

    @Test
    fun `dismissForever during suspended prepareTarget waits its turn and lands dismissal`() = runTest {
        val signal = CompletableDeferred<Unit>()
        val def = TourDefinition(
            id = TourId("test.mutex"),
            steps = listOf(
                TourStep(stepId = "first", body = "first".toCaString()),
                TourStep(
                    stepId = "second",
                    body = "second".toCaString(),
                    prepareTarget = { signal.await() },
                ),
            ),
        )
        val ctrl = controller()
        ctrl.start(def)
        val nextJob = launch { ctrl.next() } // suspends inside prepareTarget under the mutex
        runCurrentUntilIdle()
        val dismissJob = launch { ctrl.dismissForever() } // will queue behind the mutex
        runCurrentUntilIdle()
        // dismissForever() is blocked: session still active, no dismissal yet.
        ctrl.session.value shouldBe TourSessionAt(def, 0)
        prefsFlow.value.dismissed shouldBe emptySet()
        // Releasing prepareTarget allows next() to finish, then dismissForever() runs.
        signal.complete(Unit)
        nextJob.join()
        dismissJob.join()
        ctrl.session.value shouldBe null
        prefsFlow.value.dismissed shouldBe setOf(def.id.raw)
    }
}

@Serializable
private data class TestRoute(val tag: String) : NavKey

private fun TourSessionAt(def: TourDefinition, index: Int) =
    eu.darken.sdmse.common.compose.tour.TourSession(def, index)

private fun TestScope.runCurrentUntilIdle() {
    testScheduler.advanceUntilIdle()
}
