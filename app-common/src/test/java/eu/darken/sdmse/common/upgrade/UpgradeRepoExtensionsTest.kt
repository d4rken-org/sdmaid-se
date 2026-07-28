package eu.darken.sdmse.common.upgrade

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.launch

class UpgradeRepoExtensionsTest : BaseTest() {

    private class FakeInfo(
        override val isPro: Boolean,
        override val isSettled: Boolean,
        override val error: Throwable? = null,
    ) : UpgradeRepo.Info {
        override val type: UpgradeRepo.Type = UpgradeRepo.Type.FOSS
        override val upgradedAt: Instant? = null
    }

    private class FakeRepo(
        pro: Boolean,
        settled: Boolean,
        error: Throwable? = null,
    ) : UpgradeRepo {
        // Settledness rides the Info itself — a single flow, like the production repos.
        val infoFlow = MutableStateFlow<UpgradeRepo.Info>(FakeInfo(pro, settled, error))
        var refreshCalls = 0

        // What a refresh() round-trip does before returning: production ones can hang, publish a
        // new Info a dispatcher turn later, or land in an error state.
        var onRefresh: suspend FakeRepo.() -> Unit = {}

        override val storeSite: String = ""
        override val upgradeSite: String = ""
        override val betaSite: String = ""
        override val upgradeInfo: Flow<UpgradeRepo.Info> = infoFlow
        override suspend fun refresh() {
            refreshCalls++
            onRefresh()
        }

        fun settle(pro: Boolean, error: Throwable? = null) {
            infoFlow.value = FakeInfo(pro, isSettled = true, error = error)
        }
    }

    // region isProSettled (backend gate)

    @Test
    fun `a known pro user resolves true without refreshing`() = runTest {
        val repo = FakeRepo(pro = true, settled = false)

        repo.isProSettled() shouldBe true

        repo.refreshCalls shouldBe 0
        currentTime shouldBe 0
    }

    @Test
    fun `a pro state published after the refresh returned still counts`() = runTest {
        // The refresh discovers the purchase, but the shared upgradeInfo pipeline publishes the new
        // Info a dispatcher turn later. Waiting on a settled-predicate would have matched the
        // replayed pre-refresh emission and denied a paying user.
        val repo = FakeRepo(pro = false, settled = true)
        repo.onRefresh = {
            backgroundScope.launch { settle(pro = true) }
        }

        repo.isProSettled() shouldBe true
    }

    @Test
    fun `a pro state appearing within the window resolves true`() = runTest {
        val repo = FakeRepo(pro = false, settled = false)
        backgroundScope.launch {
            delay(500)
            repo.settle(pro = true)
        }

        repo.isProSettled() shouldBe true
        currentTime shouldBe 500
    }

    @Test
    fun `a settled state without a purchase denies after the full window`() = runTest {
        // The realistic "free user reached a Pro-only path" case — the only one that denies.
        val repo = FakeRepo(pro = false, settled = true)

        repo.isProSettled() shouldBe false
        currentTime shouldBe 5_000
    }

    @Test
    fun `billing that never settles fails open`() = runTest {
        // Unsettled means "couldn't verify", which must not be turned into "not entitled".
        val repo = FakeRepo(pro = false, settled = false)

        repo.isProSettled() shouldBe true
        currentTime shouldBe 5_000
    }

    @Test
    fun `an initial settled error state fails open`() = runTest {
        val repo = FakeRepo(pro = false, settled = true, error = IllegalStateException("billing broke"))

        repo.isProSettled() shouldBe true
    }

    @Test
    fun `an error published by the refresh fails open`() = runTest {
        val repo = FakeRepo(pro = false, settled = false)
        repo.onRefresh = { settle(pro = false, error = IllegalStateException("billing broke")) }

        repo.isProSettled() shouldBe true
    }

    @Test
    fun `a pro state published by a hanging refresh still resolves true`() = runTest {
        // The refresh publishes the purchase to the pipeline but its round-trip never returns, so
        // the isPro wait (which only starts after refresh()) never runs: the post-window read is
        // the only place that can see the pro state — it must honor it, not deny off isSettled.
        val repo = FakeRepo(pro = false, settled = true)
        repo.onRefresh = {
            settle(pro = true)
            awaitCancellation()
        }

        repo.isProSettled() shouldBe true
        currentTime shouldBe 5_000
    }

    @Test
    fun `a hanging refresh fails open within the supplied budget`() = runTest {
        // The timeout covers refresh + wait: the old shape started the wait only AFTER an unbounded
        // refresh, so a hanging Play call could park a task-submit gate far past the window.
        val repo = FakeRepo(pro = false, settled = false)
        repo.onRefresh = { awaitCancellation() }

        repo.isProSettled(timeout = 2.seconds) shouldBe true
        currentTime shouldBe 2_000
    }

    @Test
    fun `cancellation during the reconciliation propagates instead of failing open`() = runTest {
        val repo = FakeRepo(pro = false, settled = false)
        repo.onRefresh = { awaitCancellation() }

        val gate = async { repo.isProSettled() }
        runCurrent()
        gate.cancel()

        shouldThrow<CancellationException> { gate.await() }
    }

    @Test
    fun `isProSettled errors fail open`() = runTest {
        val repo = object : UpgradeRepo {
            override val storeSite: String = ""
            override val upgradeSite: String = ""
            override val betaSite: String = ""
            override val upgradeInfo: Flow<UpgradeRepo.Info> get() = throw IllegalStateException("billing exploded")
            override suspend fun refresh() = Unit
        }

        repo.isProSettled() shouldBe true
    }

    // endregion

    @Test
    fun `pro user resolves true without waiting`() = runTest {
        val repo = FakeRepo(pro = true, settled = false)
        repo.isProForUi() shouldBe true
        currentTime shouldBe 0
    }

    @Test
    fun `settled non-pro resolves false without waiting`() = runTest {
        // The whole point over isProSettled: a free user's tap must route to the upgrade screen
        // immediately, not after a timeout spent waiting for a Pro state that never comes.
        val repo = FakeRepo(pro = false, settled = true)
        repo.isProForUi() shouldBe false
        currentTime shouldBe 0
    }

    @Test
    fun `unsettled billing waits and honors the late pro result`() = runTest {
        // GPlay cold start: upgradeInfo reports non-Pro until the first billing result. A paying
        // user must not be bounced to the upgrade screen by that race.
        val repo = FakeRepo(pro = false, settled = false)

        launch {
            advanceTimeBy(500)
            repo.settle(pro = true)
        }

        repo.isProForUi() shouldBe true
    }

    @Test
    fun `unsettled billing waits and honors the late non-pro result`() = runTest {
        val repo = FakeRepo(pro = false, settled = false)

        launch {
            advanceTimeBy(500)
            repo.settle(pro = false)
        }

        repo.isProForUi() shouldBe false
    }

    @Test
    fun `the settle decision reads ownership off the settling Info itself`() = runTest {
        // The old two-step (wait on isSettled, re-read upgradeInfo) could pair the settle signal
        // with a stale replay. Now the Info that satisfies the settled-wait IS the decision.
        val repo = FakeRepo(pro = false, settled = false)

        launch {
            advanceTimeBy(500)
            repo.infoFlow.value = FakeInfo(isPro = true, isSettled = true)
        }

        repo.isProForUi() shouldBe true
    }

    @Test
    fun `billing that never settles falls back to non-pro after the timeout`() = runTest {
        val repo = FakeRepo(pro = false, settled = false)
        repo.isProForUi() shouldBe false
        currentTime shouldBe 3_000
    }

    @Test
    fun `cancellation during the settle wait propagates instead of failing open`() = runTest {
        // A destroyed caller (ViewModel gone mid-wait) must not continue down the Pro path.
        val repo = FakeRepo(pro = false, settled = false)

        val gate = async { repo.isProForUi() }
        runCurrent()
        gate.cancel()

        shouldThrow<CancellationException> { gate.await() }
    }

    @Test
    fun `errors fail open`() = runTest {
        val repo = object : UpgradeRepo {
            override val storeSite: String = ""
            override val upgradeSite: String = ""
            override val betaSite: String = ""
            override val upgradeInfo: Flow<UpgradeRepo.Info> get() = throw IllegalStateException("billing exploded")
            override suspend fun refresh() = Unit
        }
        repo.isProForUi() shouldBe true
    }
}
