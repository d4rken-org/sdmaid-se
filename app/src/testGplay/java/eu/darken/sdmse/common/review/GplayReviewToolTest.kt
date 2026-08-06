package eu.darken.sdmse.common.review

import android.app.Activity
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import java.time.Duration
import java.time.Instant

class GplayReviewToolTest : BaseTest() {

    private val manager = mockk<ReviewManager>()
    private val settings = mockk<ReviewSettings>()
    private val upgradeRepo = mockk<UpgradeRepo>()
    private lateinit var lastDismissedMock: DataStoreValue<Instant?>
    private lateinit var reviewedAtMock: DataStoreValue<Instant?>
    private lateinit var lastDismissedFlow: MutableStateFlow<Instant?>

    // Relaxed so the `.value(new)` writes (which go through `update`) succeed and can be verified.
    private fun <T> rwSetting(initial: T): DataStoreValue<T> = mockk<DataStoreValue<T>>(relaxed = true).apply {
        every { flow } returns flowOf(initial)
    }

    // Hot variant: a value written after the tool started collecting has to reach the pipeline,
    // which a one-shot `flowOf` can't do.
    private fun <T> hotSetting(source: Flow<T>): DataStoreValue<T> = mockk<DataStoreValue<T>>(relaxed = true).apply {
        every { flow } returns source
    }

    // Stored data that can't be decoded: the settings are created without a default fallback, so
    // reading them throws (see ReviewSettingsTest).
    private fun <T> unreadableSetting(error: Throwable): DataStoreValue<T> =
        mockk<DataStoreValue<T>>(relaxed = true).apply {
            every { flow } returns flow { throw error }
        }

    // The tool's own scope has to run on the test scheduler, otherwise the probe backoff and the
    // state throttle would burn real time.
    private fun TestScope.tool(
        upgradedAt: Instant? = Instant.now().minus(Duration.ofDays(30)),
        lastDismissed: Instant? = null,
        reviewedAt: Instant? = null,
        reviewedAtError: Throwable? = null,
        minDuration: Duration? = null,
        hotSettings: Boolean = false,
    ): GplayReviewTool {
        lastDismissedFlow = MutableStateFlow(lastDismissed)
        lastDismissedMock = when {
            hotSettings -> hotSetting(lastDismissedFlow)
            else -> rwSetting(lastDismissed)
        }
        reviewedAtMock = when (reviewedAtError) {
            null -> rwSetting(reviewedAt)
            else -> unreadableSetting(reviewedAtError)
        }
        every { settings.lastDismissed } returns lastDismissedMock
        every { settings.reviewedAt } returns reviewedAtMock

        val upgradeInfo = mockk<UpgradeRepo.Info>()
        every { upgradeInfo.upgradedAt } returns upgradedAt
        every { upgradeRepo.upgradeInfo } returns flowOf(upgradeInfo)

        return GplayReviewTool(
            appScope = backgroundScope,
            settings = settings,
            manager = manager,
            upgradeRepo = upgradeRepo,
        ).apply {
            probeRetryDelay = PROBE_RETRY_DELAY
            probeFailureCooldown = PROBE_FAILURE_COOLDOWN
            requestTimeout = REQUEST_TIMEOUT
            launchTimeout = LAUNCH_TIMEOUT
            minDuration?.let { reviewMinDuration = it }
        }
    }

    private fun reviewInfo(canShow: Boolean = true): ReviewInfo {
        val info = mockk<ReviewInfo>()
        // There is no public accessor for the isNoOp flag, the tool sniffs the toString().
        every { info.toString() } returns when {
            canShow -> "ReviewInfo{pendingIntent=PendingIntent{1}, isNoOp=false}"
            else -> "ReviewInfo{pendingIntent=null, isNoOp=true}"
        }
        return info
    }

    private fun activity(finishing: Boolean = false, destroyed: Boolean = false) = mockk<Activity>().apply {
        every { isFinishing } returns finishing
        every { isDestroyed } returns destroyed
    }

    private fun launchOk(): Task<Void?> = Tasks.forResult(null)

    // A Play call that never comes back: the ktx wrapper suspends on the listeners it registers
    // with the Task, and a relaxed mock never invokes them.
    private fun <T> hangingTask(): Task<T> = mockk<Task<T>>(relaxed = true).apply {
        every { isComplete } returns false
    }

    // The `onStart` seed is not a computed state, every assertion has to await the first real one.
    private suspend fun GplayReviewTool.computedState() = state.drop(1).first()

    // Live view of everything the tool emitted so far, for assertions that have to move the clock
    // between emissions instead of awaiting a single one.
    private fun TestScope.collectStates(tool: GplayReviewTool): List<ReviewTool.State> {
        val states = mutableListOf<ReviewTool.State>()
        backgroundScope.launch { tool.state.collect { states += it } }
        return states
    }

    // The tool's flows all run on the background scope, and `advanceUntilIdle` only advances while
    // there is foreground work, so every wait has to name the span it is waiting for.
    private fun TestScope.advanceBy(duration: Duration) {
        advanceTimeBy(duration.toMillis())
        runCurrent()
    }

    @Test fun `an eligible user is asked for a review`() = runTest2 {
        // Pins the fix: the old release-party gate could never open (nothing writes releasePartyAt
        // anymore), so this state was unreachable for every post-v1_0 install.
        every { manager.requestReviewFlow() } returns Tasks.forResult(reviewInfo())

        tool().computedState().apply {
            shouldAskForReview shouldBe true
            hasReviewed shouldBe false
        }
    }

    @Test fun `a user who has not paid for pro long enough is never probed`() = runTest2 {
        every { manager.requestReviewFlow() } returns Tasks.forResult(reviewInfo())

        tool(upgradedAt = Instant.now().minus(Duration.ofDays(3)))
            .computedState().shouldAskForReview shouldBe false

        // Eligibility is decided locally first: Play's request quota is only spent on candidates.
        verify(exactly = 0) { manager.requestReviewFlow() }
    }

    @Test fun `a recently dismissed card is not shown again`() = runTest2 {
        every { manager.requestReviewFlow() } returns Tasks.forResult(reviewInfo())

        tool(lastDismissed = Instant.now().minus(Duration.ofDays(3)))
            .computedState().shouldAskForReview shouldBe false

        verify(exactly = 0) { manager.requestReviewFlow() }
    }

    @Test fun `a user who already reviewed is never asked or probed again`() = runTest2 {
        every { manager.requestReviewFlow() } returns Tasks.forResult(reviewInfo())

        tool(reviewedAt = Instant.now().minus(Duration.ofDays(200))).computedState().apply {
            shouldAskForReview shouldBe false
            // The card is gone for good, but the fact stays readable for anything else that asks.
            hasReviewed shouldBe true
        }

        verify(exactly = 0) { manager.requestReviewFlow() }
    }

    @Test fun `reviewNow launches with a freshly requested ReviewInfo`() = runTest2 {
        // Pins the fix: ReviewInfo is short lived, the token used for the launch must not be the
        // one the availability probe obtained (potentially hours) earlier.
        val probeInfo = reviewInfo()
        val freshInfo = reviewInfo()
        every { manager.requestReviewFlow() } returnsMany listOf(
            Tasks.forResult(probeInfo),
            Tasks.forResult(freshInfo),
        )
        every { manager.launchReviewFlow(any(), any()) } returns launchOk()
        val tool = tool()
        val activity = activity()

        tool.computedState().shouldAskForReview shouldBe true
        tool.reviewNow(activity)

        verify(exactly = 2) { manager.requestReviewFlow() }
        verify(exactly = 1) { manager.launchReviewFlow(activity, freshInfo) }
        verify(exactly = 0) { manager.launchReviewFlow(activity, probeInfo) }
    }

    @Test fun `a failed fresh request keeps the card and persists nothing`() = runTest2 {
        every { manager.requestReviewFlow() } returns Tasks.forException(RuntimeException("Play unavailable"))
        val tool = tool()

        tool.reviewNow(activity())

        verify(exactly = 0) { manager.launchReviewFlow(any(), any()) }
        // A transient failure is not user intent, the next tap has to be able to retry.
        coVerify(exactly = 0) { lastDismissedMock.update(any()) }
        coVerify(exactly = 0) { reviewedAtMock.update(any()) }
    }

    @Test fun `a fresh isNoOp answer snoozes the card`() = runTest2 {
        every { manager.requestReviewFlow() } returns Tasks.forResult(reviewInfo(canShow = false))
        val tool = tool()

        tool.reviewNow(activity())

        verify(exactly = 0) { manager.launchReviewFlow(any(), any()) }
        // isNoOp is Play's quota verdict, asking again right away would be pointless.
        coVerify(exactly = 1) { lastDismissedMock.update(any()) }
        coVerify(exactly = 0) { reviewedAtMock.update(any()) }
    }

    @Test fun `a failed launch persists nothing and does not escape`() = runTest2 {
        every { manager.requestReviewFlow() } returns Tasks.forResult(reviewInfo())
        every { manager.launchReviewFlow(any(), any()) } returns Tasks.forException(RuntimeException("launch failed"))
        val tool = tool()

        tool.reviewNow(activity())

        coVerify(exactly = 0) { lastDismissedMock.update(any()) }
        coVerify(exactly = 0) { reviewedAtMock.update(any()) }
    }

    @Test fun `a launch the user dismissed instantly counts as a snooze`() = runTest2 {
        every { manager.requestReviewFlow() } returns Tasks.forResult(reviewInfo())
        every { manager.launchReviewFlow(any(), any()) } returns launchOk()
        val tool = tool()

        tool.reviewNow(activity())

        // Play returns immediately when it decides not to show anything, that is not a review.
        coVerify(exactly = 1) { lastDismissedMock.update(any()) }
        coVerify(exactly = 0) { reviewedAtMock.update(any()) }
    }

    @Test fun `a launch the user stayed in counts as a completed review`() = runTest2 {
        every { manager.requestReviewFlow() } returns Tasks.forResult(reviewInfo())
        // Real sleep on purpose: the heuristic measures wall clock, virtual time cannot reach it.
        every { manager.launchReviewFlow(any(), any()) } answers {
            Thread.sleep(150)
            launchOk()
        }
        val tool = tool(minDuration = Duration.ofMillis(50))

        tool.reviewNow(activity())

        coVerify(exactly = 1) { reviewedAtMock.update(any()) }
        coVerify(exactly = 0) { lastDismissedMock.update(any()) }
    }

    @Test fun `a dead activity aborts the launch without persisting`() = runTest2 {
        every { manager.requestReviewFlow() } returns Tasks.forResult(reviewInfo())
        every { manager.launchReviewFlow(any(), any()) } returns launchOk()
        val tool = tool()

        // The fresh request is a Play round-trip, the activity can be gone by the time it returns.
        tool.reviewNow(activity(finishing = true))

        verify(exactly = 0) { manager.launchReviewFlow(any(), any()) }
        coVerify(exactly = 0) { lastDismissedMock.update(any()) }
        coVerify(exactly = 0) { reviewedAtMock.update(any()) }
    }

    @Test fun `overlapping reviewNow calls launch the flow only once`() = runTest2 {
        // Park the first call on an unresolved Play request, so the second genuinely overlaps it.
        val successListener = slot<OnSuccessListener<in ReviewInfo>>()
        val pendingRequest = mockk<Task<ReviewInfo>>().apply {
            every { isComplete } returns false
            every { addOnSuccessListener(capture(successListener)) } returns this
            every { addOnFailureListener(any<OnFailureListener>()) } returns this
        }
        every { manager.requestReviewFlow() } returnsMany listOf(
            pendingRequest,
            // A second request would resolve instantly, so a broken guard shows up as a launch.
            Tasks.forResult(reviewInfo()),
        )
        every { manager.launchReviewFlow(any(), any()) } returns launchOk()
        val tool = tool()
        val activity = activity()

        // runCurrent, not advanceUntilIdle: the first call has to be parked on the request when the
        // second tap arrives, an unbounded time advance would trip the request timeout first.
        val first = launch { tool.reviewNow(activity) }
        runCurrent()

        tool.reviewNow(activity)

        successListener.captured.onSuccess(reviewInfo())
        first.join()

        // Without the single-flight guard the second tap would run its own request+launch, and
        // Play's flow would pop up again the moment the user returned from the first one.
        verify(exactly = 1) { manager.requestReviewFlow() }
        verify(exactly = 1) { manager.launchReviewFlow(any(), any()) }
    }

    @Test fun `a probe that fails once still resolves within the retry budget`() = runTest2 {
        every { manager.requestReviewFlow() } returnsMany listOf(
            Tasks.forException(RuntimeException("Play unavailable")),
            Tasks.forResult(reviewInfo()),
        )

        tool().computedState().shouldAskForReview shouldBe true

        verify(exactly = 2) { manager.requestReviewFlow() }
    }

    @Test fun `a probe that keeps failing hides the card`() = runTest2 {
        every { manager.requestReviewFlow() } returns Tasks.forException(RuntimeException("Play unavailable"))

        tool().computedState().shouldAskForReview shouldBe false

        verify(exactly = 3) { manager.requestReviewFlow() }
    }

    @Test fun `an isNoOp probe answer is accepted instead of retried`() = runTest2 {
        every { manager.requestReviewFlow() } returns Tasks.forResult(reviewInfo(canShow = false))

        tool().computedState().shouldAskForReview shouldBe false

        // isNoOp is an answer, not a failure: burning the retry budget on it would just cost quota.
        verify(exactly = 1) { manager.requestReviewFlow() }
    }

    @Test fun `unreadable settings fall back to the default state`() = runTest2 {
        // `state` is shared on AppScope, which has no exception handler: an error escaping the
        // pipeline would take the process down instead of reaching any collector, so a collector
        // side `.catch` can never see it.
        val tool = tool(reviewedAtError = SerializationException("Stored timestamp is corrupt"))

        // The onStart seed plus the fallback the tool emits in place of the failure.
        tool.state.take(2).toList() shouldBe listOf(ReviewTool.State(), ReviewTool.State())
    }

    @Test fun `cancellation during reviewNow is not swallowed`() = runTest2 {
        every { manager.requestReviewFlow() } throws CancellationException("scope died")
        val tool = tool()

        shouldThrow<CancellationException> { tool.reviewNow(activity()) }

        // A cancelled coroutine is not a Play failure: no launch, no bookkeeping.
        verify(exactly = 0) { manager.launchReviewFlow(any(), any()) }
        coVerify(exactly = 0) { lastDismissedMock.update(any()) }
        coVerify(exactly = 0) { reviewedAtMock.update(any()) }
    }

    @Test fun `cancellation during the probe is not swallowed into the retry path`() = runTest2 {
        every { manager.requestReviewFlow() } throws CancellationException("scope died")
        val tool = tool()

        // Cancellation takes the probe down with its scope, no verdict is ever computed (virtual
        // time, so the timeout costs nothing).
        val computed = withTimeoutOrNull(Duration.ofMinutes(1).toMillis()) { tool.computedState() }

        computed shouldBe null
        // The general failure path would have burned all 3 attempts and settled on "unavailable".
        verify(exactly = 1) { manager.requestReviewFlow() }
    }

    @Test fun `a probe that times out abandons the whole round`() = runTest2 {
        var hanging = true
        every { manager.requestReviewFlow() } answers {
            if (hanging) hangingTask() else Tasks.forResult(reviewInfo())
        }
        val tool = tool()
        val states = collectStates(tool)

        advanceBy(REQUEST_TIMEOUT.multipliedBy(2))

        // Our timeout does not cancel the Play Task: an in-round retry would stack concurrent
        // quota-consuming requests against a service that is already hung.
        verify(exactly = 1) { manager.requestReviewFlow() }
        states.last().shouldAskForReview shouldBe false

        hanging = false
        advanceBy(PROBE_FAILURE_COOLDOWN.multipliedBy(2))

        // Recovery comes from the next cooldown round, not from the abandoned one.
        verify(exactly = 2) { manager.requestReviewFlow() }
        states.last().shouldAskForReview shouldBe true
    }

    @Test fun `a probe exception is still retried inside the round`() = runTest2 {
        // Only a timeout abandons the round, the exception path keeps its 3 attempt budget.
        every { manager.requestReviewFlow() } returnsMany listOf(
            Tasks.forException(RuntimeException("Play unavailable")),
            Tasks.forResult(reviewInfo()),
        )
        val tool = tool()
        val states = collectStates(tool)

        advanceBy(PROBE_RETRY_DELAY.multipliedBy(4))

        verify(exactly = 2) { manager.requestReviewFlow() }
        states.last().shouldAskForReview shouldBe true
    }

    @Test fun `a hanging fresh request releases the lock without persisting`() = runTest2 {
        var hanging = true
        every { manager.requestReviewFlow() } answers {
            if (hanging) hangingTask() else Tasks.forResult(reviewInfo())
        }
        every { manager.launchReviewFlow(any(), any()) } returns launchOk()
        val tool = tool()
        val activity = activity()

        tool.reviewNow(activity)

        // A hang is not user intent: no launch, no bookkeeping, the next tap has to be able to retry.
        verify(exactly = 0) { manager.launchReviewFlow(any(), any()) }
        coVerify(exactly = 0) { lastDismissedMock.update(any()) }
        coVerify(exactly = 0) { reviewedAtMock.update(any()) }

        hanging = false
        tool.reviewNow(activity)

        // Without the timeout the single-flight lock would still be held by the first tap.
        verify(exactly = 1) { manager.launchReviewFlow(activity, any()) }
    }

    @Test fun `a hanging launch releases the lock without persisting`() = runTest2 {
        every { manager.requestReviewFlow() } returns Tasks.forResult(reviewInfo())
        var hanging = true
        every { manager.launchReviewFlow(any(), any()) } answers {
            if (hanging) hangingTask() else launchOk()
        }
        val tool = tool()
        val activity = activity()

        tool.reviewNow(activity)

        // The outcome is unknown, so neither the review nor the snooze may be recorded, and the
        // duration heuristic must not treat the timeout as a completed review.
        coVerify(exactly = 0) { reviewedAtMock.update(any()) }
        coVerify(exactly = 0) { lastDismissedMock.update(any()) }

        hanging = false
        tool.reviewNow(activity)

        verify(exactly = 2) { manager.launchReviewFlow(activity, any()) }
    }

    @Test fun `a dismiss racing the fresh request aborts the launch`() = runTest2 {
        val tool = tool()
        every { manager.requestReviewFlow() } answers {
            // The user hits "maybe later" while Play is still answering the review tap.
            runBlocking { tool.dismiss() }
            Tasks.forResult(reviewInfo())
        }
        every { manager.launchReviewFlow(any(), any()) } returns launchOk()

        tool.reviewNow(activity())

        verify(exactly = 0) { manager.launchReviewFlow(any(), any()) }
        // Only the dismiss' own write, reviewNow must not add bookkeeping on top of it.
        coVerify(exactly = 1) { lastDismissedMock.update(any()) }
        coVerify(exactly = 0) { reviewedAtMock.update(any()) }
    }

    @Test fun `a definitive probe answer is not requested twice`() = runTest2 {
        every { manager.requestReviewFlow() } returns Tasks.forResult(reviewInfo())
        val tool = tool()

        val first = mutableListOf<ReviewTool.State>()
        val firstJob = backgroundScope.launch { tool.state.collect { first += it } }
        advanceBy(Duration.ofSeconds(1))
        first.last().shouldAskForReview shouldBe true
        firstJob.cancelAndJoin()

        val second = mutableListOf<ReviewTool.State>()
        val secondJob = backgroundScope.launch { tool.state.collect { second += it } }
        advanceBy(Duration.ofSeconds(1))
        second.last().shouldAskForReview shouldBe true
        secondJob.cancelAndJoin()

        // Play's answer holds for the rest of the process, a re-subscription must not cost quota.
        verify(exactly = 1) { manager.requestReviewFlow() }
    }

    @Test fun `an isNoOp answer is not requested twice either`() = runTest2 {
        every { manager.requestReviewFlow() } returns Tasks.forResult(reviewInfo(canShow = false))
        val tool = tool()

        val first = mutableListOf<ReviewTool.State>()
        val firstJob = backgroundScope.launch { tool.state.collect { first += it } }
        advanceBy(Duration.ofSeconds(1))
        first.last().shouldAskForReview shouldBe false
        firstJob.cancelAndJoin()

        val second = mutableListOf<ReviewTool.State>()
        val secondJob = backgroundScope.launch { tool.state.collect { second += it } }
        advanceBy(Duration.ofSeconds(1))
        second.last().shouldAskForReview shouldBe false
        secondJob.cancelAndJoin()

        // isNoOp is a verdict, not a failure: it is cached like any other answer.
        verify(exactly = 1) { manager.requestReviewFlow() }
    }

    @Test fun `a transient probe failure is retried after the cooldown`() = runTest2 {
        var healthy = false
        every { manager.requestReviewFlow() } answers {
            if (healthy) Tasks.forResult(reviewInfo()) else Tasks.forException(RuntimeException("Play unavailable"))
        }
        val tool = tool()
        val states = collectStates(tool)

        advanceBy(PROBE_FAILURE_COOLDOWN.dividedBy(2))

        verify(exactly = 3) { manager.requestReviewFlow() }
        states.last().shouldAskForReview shouldBe false

        healthy = true
        advanceBy(PROBE_FAILURE_COOLDOWN)

        // A failure is not an answer, so Play gets asked again once the cooldown is over.
        verify(exactly = 4) { manager.requestReviewFlow() }
        states.last().shouldAskForReview shouldBe true
    }

    @Test fun `the probe retry rounds are bounded`() = runTest2 {
        every { manager.requestReviewFlow() } returns Tasks.forException(RuntimeException("Play unavailable"))
        val tool = tool(hotSettings = true)
        val states = collectStates(tool)

        advanceBy(PROBE_FAILURE_COOLDOWN.multipliedBy(4))

        // The initial round plus 3 cooldown rounds, 3 attempts each, then the process gives up.
        verify(exactly = 12) { manager.requestReviewFlow() }
        states.last().shouldAskForReview shouldBe false

        advanceBy(PROBE_FAILURE_COOLDOWN.multipliedBy(20))

        verify(exactly = 12) { manager.requestReviewFlow() }

        // The budget is spent for the process: an eligibility flicker restarts the probe branch,
        // but it must not hand out a fresh set of rounds.
        lastDismissedFlow.value = Instant.now()
        advanceBy(Duration.ofSeconds(1))
        lastDismissedFlow.value = null
        advanceBy(PROBE_FAILURE_COOLDOWN.multipliedBy(10))

        verify(exactly = 12) { manager.requestReviewFlow() }
    }

    @Test fun `a snooze running out flips the card on without a restart`() = runTest2 {
        every { manager.requestReviewFlow() } returns Tasks.forResult(reviewInfo())
        var fakeNow = BASE_NOW
        val tool = tool(
            upgradedAt = BASE_NOW.minus(Duration.ofDays(60)),
            lastDismissed = BASE_NOW.minus(Duration.ofDays(13)),
        ).apply { nowProvider = { fakeNow } }
        val states = collectStates(tool)

        advanceBy(Duration.ofSeconds(1))
        states.last().shouldAskForReview shouldBe false

        // The snooze ends a day later: the scheduled re-evaluation has to re-read the clock.
        fakeNow = BASE_NOW.plus(Duration.ofDays(2))
        advanceBy(Duration.ofDays(2))

        states.last().shouldAskForReview shouldBe true
    }

    @Test fun `the pro grace period boundary is strict`() = runTest2 {
        every { manager.requestReviewFlow() } returns Tasks.forResult(reviewInfo())
        var fakeNow = BASE_NOW
        val upgradedAt = BASE_NOW.minus(Duration.ofDays(21))

        val atBoundary = tool(upgradedAt = upgradedAt).apply { nowProvider = { fakeNow } }
        val atBoundaryStates = collectStates(atBoundary)
        advanceBy(Duration.ofSeconds(1))
        // 21 days have to have passed, not just been reached
        atBoundaryStates.last().shouldAskForReview shouldBe false

        // A second instance because nothing is pending on the first one: the wake schedule only
        // keeps boundaries strictly after "now", and this one is exactly "now".
        fakeNow = BASE_NOW.plus(Duration.ofSeconds(1))
        val pastBoundary = tool(upgradedAt = upgradedAt).apply { nowProvider = { fakeNow } }
        val pastBoundaryStates = collectStates(pastBoundary)
        advanceBy(Duration.ofSeconds(1))

        pastBoundaryStates.last().shouldAskForReview shouldBe true
    }

    @Test fun `a new dismiss reschedules the boundary re-evaluation`() = runTest2 {
        every { manager.requestReviewFlow() } returns Tasks.forResult(reviewInfo())
        var fakeNow = BASE_NOW
        val tool = tool(
            upgradedAt = BASE_NOW.minus(Duration.ofDays(60)),
            lastDismissed = BASE_NOW.minus(Duration.ofDays(13)),
            hotSettings = true,
        ).apply { nowProvider = { fakeNow } }
        val states = collectStates(tool)

        advanceBy(Duration.ofSeconds(1))
        states.last().shouldAskForReview shouldBe false

        // Dismissed again while the old snooze end was still scheduled
        lastDismissedFlow.value = BASE_NOW
        advanceBy(Duration.ofSeconds(1))

        fakeNow = BASE_NOW.plus(Duration.ofDays(2))
        advanceBy(Duration.ofDays(3))
        // The stale boundary must not flip the card back on, the new snooze governs now
        states.last().shouldAskForReview shouldBe false

        fakeNow = BASE_NOW.plus(Duration.ofDays(15))
        advanceBy(Duration.ofDays(15))

        states.last().shouldAskForReview shouldBe true
    }

    @Test fun `a clock that moved backwards re-evaluates instead of flipping`() = runTest2 {
        every { manager.requestReviewFlow() } returns Tasks.forResult(reviewInfo())
        var fakeNow = BASE_NOW
        val tool = tool(
            upgradedAt = BASE_NOW.minus(Duration.ofDays(60)),
            lastDismissed = BASE_NOW.minus(Duration.ofDays(13)),
        ).apply { nowProvider = { fakeNow } }
        val states = collectStates(tool)

        advanceBy(Duration.ofSeconds(1))
        states.last().shouldAskForReview shouldBe false

        // The device clock moved back: the wake fires on schedule, but the snooze is still running
        fakeNow = BASE_NOW.minus(Duration.ofDays(5))
        advanceBy(Duration.ofDays(30))

        states.last().shouldAskForReview shouldBe false
        verify(exactly = 0) { manager.requestReviewFlow() }

        // Rescheduling is capped, so a backwards clock cannot keep the process waking forever
        fakeNow = BASE_NOW.plus(Duration.ofDays(2))
        advanceBy(Duration.ofDays(30))

        states.last().shouldAskForReview shouldBe false
    }

    companion object {
        private val BASE_NOW: Instant = Instant.parse("2024-06-01T12:00:00Z")
        private val PROBE_RETRY_DELAY: Duration = Duration.ofSeconds(1)
        private val PROBE_FAILURE_COOLDOWN: Duration = Duration.ofMinutes(5)
        private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(5)
        private val LAUNCH_TIMEOUT: Duration = Duration.ofMinutes(1)
    }
}
