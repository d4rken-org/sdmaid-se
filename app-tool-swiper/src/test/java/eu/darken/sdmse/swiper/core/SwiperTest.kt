package eu.darken.sdmse.swiper.core

import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.SwiperSessionCreator
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.swiper.core.db.SwipeSessionDao
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import kotlin.time.Duration.Companion.seconds

class SwiperTest : BaseTest() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @AfterEach
    fun stopAppScope() {
        appScope.coroutineContext[Job]?.cancel()
    }

    private val paths = setOf<APath>(LocalPath.build("storage", "emulated", "0", "DCIM"))

    private fun info(isPro: Boolean, isSettled: Boolean = true) = mockk<UpgradeRepo.Info>().apply {
        every { this@apply.isPro } returns isPro
        every { this@apply.isSettled } returns isSettled
        every { error } returns null
    }

    private fun create(
        activeSessions: Int,
        upgradeInfo: MutableStateFlow<UpgradeRepo.Info>,
    ): Pair<Swiper, SwipeSessionDao> {
        val sessionDao = mockk<SwipeSessionDao>(relaxed = true).apply {
            coEvery { countActiveSessions() } returns activeSessions
        }
        val upgradeRepo = mockk<UpgradeRepo>(relaxed = true).apply {
            every { this@apply.upgradeInfo } returns upgradeInfo
        }
        val swiper = Swiper(
            appScope = appScope,
            gatewaySwitch = mockk(relaxed = true),
            scanner = mockk(relaxed = true),
            deleter = mockk(relaxed = true),
            sessionDao = sessionDao,
            itemDao = mockk(relaxed = true),
            upgradeRepo = upgradeRepo,
        )
        return swiper to sessionDao
    }

    @Test
    fun `free user below the session limit can create a session`() = runTest2 {
        val (swiper, dao) = create(
            activeSessions = SwiperSettings.FREE_VERSION_SESSION_LIMIT - 1,
            upgradeInfo = MutableStateFlow(info(isPro = false)),
        )

        swiper.createSession(paths).shouldBeInstanceOf<SwiperSessionCreator.Result.Created>()

        coVerify(exactly = 1) { dao.insert(any()) }
    }

    @Test
    fun `free user at the session limit is refused`() = runTest2 {
        val (swiper, dao) = create(
            activeSessions = SwiperSettings.FREE_VERSION_SESSION_LIMIT,
            upgradeInfo = MutableStateFlow(info(isPro = false)),
        )

        swiper.createSession(paths) shouldBe SwiperSessionCreator.Result.LimitReached

        coVerify(exactly = 0) { dao.insert(any()) }
    }

    @Test
    fun `pro user past the session limit can create a session`() = runTest2 {
        val (swiper, dao) = create(
            activeSessions = SwiperSettings.FREE_VERSION_SESSION_LIMIT + 3,
            upgradeInfo = MutableStateFlow(info(isPro = true)),
        )

        swiper.createSession(paths).shouldBeInstanceOf<SwiperSessionCreator.Result.Created>()

        coVerify(exactly = 1) { dao.insert(any()) }
    }

    @Test
    fun `pro user at the session limit is not refused while billing is still connecting`() = runTest2 {
        val upgradeInfo = MutableStateFlow(info(isPro = false, isSettled = false))
        val (swiper, dao) = create(
            activeSessions = SwiperSettings.FREE_VERSION_SESSION_LIMIT,
            upgradeInfo = upgradeInfo,
        )
        launch {
            delay(1.seconds)
            upgradeInfo.value = info(isPro = true)
        }

        swiper.createSession(paths).shouldBeInstanceOf<SwiperSessionCreator.Result.Created>()

        coVerify(exactly = 1) { dao.insert(any()) }
    }

    @Test
    fun `session limit applies when billing never settles`() = runTest2 {
        val (swiper, dao) = create(
            activeSessions = SwiperSettings.FREE_VERSION_SESSION_LIMIT,
            upgradeInfo = MutableStateFlow(info(isPro = false, isSettled = false)),
        )

        swiper.createSession(paths) shouldBe SwiperSessionCreator.Result.LimitReached

        coVerify(exactly = 0) { dao.insert(any()) }
    }
}
