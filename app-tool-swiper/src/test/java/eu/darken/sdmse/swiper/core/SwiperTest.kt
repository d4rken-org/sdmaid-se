package eu.darken.sdmse.swiper.core

import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.DeletedPathsListener
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.SwiperSessionCreator
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.swiper.core.db.SwipeItemDao
import eu.darken.sdmse.swiper.core.db.SwipeItemEntity
import eu.darken.sdmse.swiper.core.db.SwipeSessionDao
import eu.darken.sdmse.swiper.core.deleter.SwiperDeleter
import eu.darken.sdmse.swiper.core.tasks.SwiperDeleteTask
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import javax.inject.Provider
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
            deletedPathsListener = mockk(relaxed = true),
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

    private fun createForDelete(
        decisions: List<Pair<APath, SwipeDecision>>,
        deleterResult: SwiperDeleter.Result,
        listener: DeletedPathsListener,
    ): Swiper {
        val entities = decisions.mapIndexed { index, (path, decision) ->
            SwipeItemEntity(
                id = index.toLong() + 1,
                sessionId = SESSION_ID,
                itemIndex = index,
                path = path,
                decision = decision,
            )
        }
        val itemDao = mockk<SwipeItemDao>(relaxed = true).apply {
            coEvery { getItemsForSessionSync(SESSION_ID) } returns entities
        }
        val gatewaySwitch = mockk<GatewaySwitch>(relaxed = true).apply {
            coEvery { lookup(any()) } answers {
                mockk<APathLookup<APath>>(relaxed = true).apply { every { lookedUp } returns firstArg() }
            }
        }
        val deleter = mockk<SwiperDeleter>().apply {
            every { progress } returns flowOf(null)
            coEvery { delete(any(), any()) } returns deleterResult
        }
        return Swiper(
            appScope = appScope,
            gatewaySwitch = gatewaySwitch,
            scanner = mockk(relaxed = true),
            deleter = Provider { deleter },
            sessionDao = mockk(relaxed = true),
            itemDao = itemDao,
            upgradeRepo = mockk(relaxed = true),
            deletedPathsListener = listener,
        )
    }

    @Test
    fun `delete tells the listener which paths the deleter removed`() = runTest2 {
        val photo = LocalPath.build("storage", "emulated", "0", "DCIM", "a.jpg")
        val failed = LocalPath.build("storage", "emulated", "0", "DCIM", "b.jpg")
        val listener = mockk<DeletedPathsListener>(relaxed = true)
        val swiper = createForDelete(
            decisions = listOf(photo to SwipeDecision.DELETE, failed to SwipeDecision.DELETE),
            deleterResult = SwiperDeleter.Result(
                deletedPaths = setOf(photo),
                deletedSize = 100L,
                failedPaths = setOf(failed),
            ),
            listener = listener,
        )
        swiper.refreshSessionLookups(SESSION_ID)

        swiper.submit(SwiperDeleteTask(SESSION_ID))

        coVerify(exactly = 1) { listener.onPathsDeleted(setOf(photo)) }
    }

    @Test
    fun `delete does not call the listener when nothing was deleted`() = runTest2 {
        val photo = LocalPath.build("storage", "emulated", "0", "DCIM", "a.jpg")
        val listener = mockk<DeletedPathsListener>(relaxed = true)
        val swiper = createForDelete(
            decisions = listOf(photo to SwipeDecision.DELETE),
            deleterResult = SwiperDeleter.Result(
                deletedPaths = emptySet(),
                deletedSize = 0L,
                failedPaths = setOf(photo),
            ),
            listener = listener,
        )
        swiper.refreshSessionLookups(SESSION_ID)

        swiper.submit(SwiperDeleteTask(SESSION_ID))

        coVerify(exactly = 0) { listener.onPathsDeleted(any()) }
    }

    @Test
    fun `delete without items marked for deletion does not call the listener`() = runTest2 {
        val photo = LocalPath.build("storage", "emulated", "0", "DCIM", "a.jpg")
        val listener = mockk<DeletedPathsListener>(relaxed = true)
        val swiper = createForDelete(
            decisions = listOf(photo to SwipeDecision.KEEP),
            deleterResult = SwiperDeleter.Result(
                deletedPaths = setOf(photo),
                deletedSize = 100L,
                failedPaths = emptySet(),
            ),
            listener = listener,
        )
        swiper.refreshSessionLookups(SESSION_ID)

        swiper.submit(SwiperDeleteTask(SESSION_ID))

        coVerify(exactly = 0) { listener.onPathsDeleted(any()) }
    }

    companion object {
        private const val SESSION_ID = "session"
    }
}
