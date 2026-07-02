package eu.darken.sdmse.common.upgrade

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant
import kotlinx.coroutines.launch

class UpgradeRepoExtensionsTest : BaseTest() {

    private class FakeInfo(override val isPro: Boolean) : UpgradeRepo.Info {
        override val type: UpgradeRepo.Type = UpgradeRepo.Type.FOSS
        override val upgradedAt: Instant? = null
        override val error: Throwable? = null
    }

    private class FakeRepo(
        pro: Boolean,
        settled: Boolean,
    ) : UpgradeRepo {
        val proFlow = MutableStateFlow<UpgradeRepo.Info>(FakeInfo(pro))
        val settledFlow = MutableStateFlow(settled)
        var refreshCalls = 0

        override val storeSite: String = ""
        override val upgradeSite: String = ""
        override val betaSite: String = ""
        override val upgradeInfo: Flow<UpgradeRepo.Info> = proFlow
        override val isSettled: Flow<Boolean> = settledFlow
        override suspend fun refresh() {
            refreshCalls++
        }

        fun settle(pro: Boolean) {
            proFlow.value = FakeInfo(pro)
            settledFlow.value = true
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
    fun `billing that never settles falls back to non-pro after the timeout`() = runTest {
        val repo = FakeRepo(pro = false, settled = false)
        repo.isProForUi() shouldBe false
        currentTime shouldBe 3_000
    }

    @Test
    fun `errors fail open`() = runTest {
        val repo = object : UpgradeRepo {
            override val storeSite: String = ""
            override val upgradeSite: String = ""
            override val betaSite: String = ""
            override val upgradeInfo: Flow<UpgradeRepo.Info> get() = throw IllegalStateException("billing exploded")
            override val isSettled: Flow<Boolean> = MutableStateFlow(true)
            override suspend fun refresh() = Unit
        }
        repo.isProForUi() shouldBe true
    }
}
