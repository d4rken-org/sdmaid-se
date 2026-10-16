package eu.darken.sdmse.systemcleaner.core

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Delete
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathGateway
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.ReadException
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.systemcleaner.core.filter.BaseSystemCleanerFilter
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilter
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilterException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import java.time.Instant

class SystemCrawlerTest : BaseTest() {

    private val keepAliveScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private val areaManager: DataAreaManager = mockk()
    private val gatewaySwitch: GatewaySwitch = mockk()
    private val exclusionManager: ExclusionManager = mockk()
    private val forensics: FileForensics = mockk()

    private val area1 = DataArea(
        path = LocalPath.build("/storage", "emulated", "16"),
        type = DataArea.Type.SDCARD,
        userHandle = UserHandle2(16),
    )
    private val area2 = DataArea(
        path = LocalPath.build("/storage", "emulated", "0"),
        type = DataArea.Type.SDCARD,
        userHandle = UserHandle2(0),
    )

    private val area2Item = lookup(area2.path.child("junk.tmp") as LocalPath, FileType.FILE)

    @BeforeEach
    fun setup() {
        every { gatewaySwitch.sharedResource } returns SharedResource.createKeepAlive("gw", keepAliveScope)
        every { forensics.sharedResource } returns SharedResource.createKeepAlive("ff", keepAliveScope)
        every { areaManager.state } returns flowOf(DataAreaManager.State(areas = setOf(area1, area2)))
        every { exclusionManager.exclusions } returns flowOf(emptyList())

        coEvery { gatewaySwitch.lookup(area2.path) } returns lookup(area2.path as LocalPath, FileType.DIRECTORY)
        coEvery { gatewaySwitch.walk(area2.path, any()) } returns flowOf(area2Item)
    }

    @AfterEach
    fun stopKeepAliveScope() {
        keepAliveScope.coroutineContext[Job]?.cancel()
    }

    private fun lookup(path: LocalPath, fileType: FileType) = LocalPathLookup(
        lookedUp = path,
        fileType = fileType,
        size = 1024L,
        modifiedAt = Instant.EPOCH,
        target = null,
    )

    private fun create() = SystemCrawler(
        dispatcherProvider = TestDispatcherProvider(),
        areaManager = areaManager,
        gatewaySwitch = gatewaySwitch,
        exclusionManager = exclusionManager,
        forensics = forensics,
    )

    private class MatchingFilter(
        private val onMatch: (APathLookup<*>) -> SystemCleanerFilter.Match?,
    ) : BaseSystemCleanerFilter() {
        override val identifier = "test.matching"
        override val icon = Icons.TwoTone.Delete
        override suspend fun getLabel() = "Matching".toCaString()
        override suspend fun getDescription() = "Matches by predicate".toCaString()
        override suspend fun targetAreas() = setOf(DataArea.Type.SDCARD)
        override suspend fun initialize() = Unit
        override suspend fun match(item: APathLookup<*>) = onMatch(item)
        override suspend fun process(matches: Collection<SystemCleanerFilter.Match>) =
            throw UnsupportedOperationException()
    }

    private val filter = MatchingFilter { item ->
        if (item == area2Item) SystemCleanerFilter.Match.Deletion(item) else null
    }

    @Test
    fun `an area whose root lookup fails is skipped and the other areas are still crawled`() = runTest2 {
        coEvery { gatewaySwitch.lookup(area1.path) } throws ReadException(path = area1.path)
        coEvery { gatewaySwitch.walk(area1.path, any()) } returns flow { throw ReadException(path = area1.path) }

        val result = create().crawl(setOf(filter))

        result.single().items.map { it.lookup } shouldBe listOf(area2Item)
        coVerify(exactly = 0) { gatewaySwitch.walk(area1.path, any()) }
    }

    @Test
    fun `an area whose walk cannot be created is skipped and the other areas are still crawled`() = runTest2 {
        coEvery { gatewaySwitch.lookup(area1.path) } returns lookup(area1.path as LocalPath, FileType.DIRECTORY)
        coEvery { gatewaySwitch.walk(area1.path, any()) } throws ReadException(path = area1.path)

        val result = create().crawl(setOf(filter))

        result.single().items.map { it.lookup } shouldBe listOf(area2Item)
    }

    @Test
    fun `a read error from the walk filter still fails the crawl`() = runTest2 {
        val subDir = lookup(area1.path.child("subdir") as LocalPath, FileType.DIRECTORY)
        coEvery { gatewaySwitch.lookup(area1.path) } returns lookup(area1.path as LocalPath, FileType.DIRECTORY)
        coEvery { gatewaySwitch.walk(area1.path, any()) } answers {
            val options = secondArg<APathGateway.WalkOptions<APath, APathLookup<APath>>>()
            flow { if (options.onFilter!!(subDir)) emit(subDir) }
        }
        coEvery { forensics.identifyArea(any()) } throws ReadException(path = subDir.lookedUp)

        shouldThrow<ReadException> { create().crawl(setOf(filter)) }
    }

    @Test
    fun `a non-IO error from a filter match still fails the crawl`() = runTest2 {
        coEvery { gatewaySwitch.lookup(area1.path) } returns lookup(area1.path as LocalPath, FileType.DIRECTORY)
        coEvery { gatewaySwitch.walk(area1.path, any()) } returns flowOf()
        val failing = MatchingFilter { throw IllegalStateException("broken filter") }

        shouldThrow<SystemCleanerFilterException> { create().crawl(setOf(failing)) }
    }
}
