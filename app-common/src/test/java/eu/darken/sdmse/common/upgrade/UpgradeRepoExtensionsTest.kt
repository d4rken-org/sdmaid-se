package eu.darken.sdmse.common.upgrade

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant
import kotlinx.coroutines.launch

class UpgradeRepoExtensionsTest : BaseTest() {

    private class FakeInfo(
        override val isPro: Boolean,
        override val isSettled: Boolean,
    ) : UpgradeRepo.Info {
        override val type: UpgradeRepo.Type = UpgradeRepo.Type.FOSS
        override val upgradedAt: Instant? = null
        override val error: Throwable? = null
    }

    private class FakeRepo(
        pro: Boolean,
        settled: Boolean,
    ) : UpgradeRepo {
        // Settledness rides the Info itself — a single flow, like the production repos.
        val infoFlow = MutableStateFlow<UpgradeRepo.Info>(FakeInfo(pro, settled))
        var refreshCalls = 0

        override val storeSite: String = ""
        override val upgradeSite: String = ""
        override val betaSite: String = ""
        override val upgradeInfo: Flow<UpgradeRepo.Info> = infoFlow
        override suspend fun refresh() {
            refreshCalls++
        }

        fun settle(pro: Boolean) {
            infoFlow.value = FakeInfo(pro, isSettled = true)
        }
    }

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
