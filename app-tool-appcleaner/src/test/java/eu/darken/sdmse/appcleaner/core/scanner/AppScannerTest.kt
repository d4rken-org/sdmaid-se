package eu.darken.sdmse.appcleaner.core.scanner

import eu.darken.sdmse.appcleaner.core.AppCleanerSettings
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.ReadException
import eu.darken.sdmse.common.files.Segments
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.common.user.UserManager2
import eu.darken.sdmse.common.user.UserProfile2
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.mockDataStoreValue
import java.time.Instant

/**
 * Direct coverage for [AppScanner.readAppDirs], the streaming search path walk. The public scan()
 * entry needs the full pkg/area setup and is exercised via AppCleanerTest with a mocked scanner.
 */
class AppScannerTest : BaseTest() {

    private val gatewaySwitch = mockk<GatewaySwitch>()
    private val userManager = mockk<UserManager2>().apply {
        coEvery { systemUser() } returns UserProfile2(handle = UserHandle2(handleId = 0))
    }
    private val settings = mockk<AppCleanerSettings>()

    /** Delegates matching to a lambda so tests can match everything or throw. */
    private class FakeFilter(
        private val onMatch: suspend (APathLookup<APath>) -> ExpendablesFilter.Match?,
    ) : ExpendablesFilter {
        override val progress: Flow<Progress.Data?> = MutableStateFlow(null)
        override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {}
        override suspend fun initialize() {}

        override suspend fun match(
            pkgId: Pkg.Id,
            target: APathLookup<APath>,
            areaType: DataArea.Type,
            pfpSegs: Segments,
        ): ExpendablesFilter.Match? = onMatch(target)

        override suspend fun process(
            targets: Collection<ExpendablesFilter.Match>,
            allMatches: Collection<ExpendablesFilter.Match>,
        ): ExpendablesFilter.ProcessResult = ExpendablesFilter.ProcessResult(emptyList(), emptyList())
    }

    private fun deletion(lookup: APathLookup<*>) = ExpendablesFilter.Match.Deletion(FakeFilter::class, lookup)

    private suspend fun scanner(
        filter: FakeFilter,
        minCacheAgeMs: Long = 0L,
    ): AppScanner {
        every { settings.minCacheAgeMs } returns mockDataStoreValue(minCacheAgeMs)
        val factory = object : ExpendablesFilter.Factory {
            override suspend fun isEnabled(): Boolean = true
            override suspend fun create(): ExpendablesFilter = filter
        }
        return AppScanner(
            areaManager = mockk(),
            gatewaySwitch = gatewaySwitch,
            filterFactories = setOf(factory),
            postProcessorModule = mockk(),
            context = mockk(),
            fileForensics = mockk(),
            rootManager = mockk(),
            exclusionManager = mockk(),
            pkgRepo = mockk(),
            clutterRepo = mockk(),
            settings = settings,
            inaccessibleCacheProvider = mockk(),
            userManager = userManager,
            pkgOps = mockk(),
        ).also { it.initialize() }
    }

    private val installId = InstallId(
        pkgId = Pkg.Id(name = "com.test.pkg"),
        userHandle = UserHandle2(handleId = 0),
    )

    private fun areaInfo(pkgDir: String = "com.test.pkg"): AreaInfo {
        val prefix = LocalPath.build("/storage/emulated/0/Android/data")
        return AreaInfo(
            file = prefix.child(pkgDir),
            prefix = prefix,
            dataArea = DataArea(
                path = prefix,
                type = DataArea.Type.PUBLIC_DATA,
                userHandle = UserHandle2(handleId = 0),
            ),
            isBlackListLocation = true,
        )
    }

    private fun lookup(
        path: String,
        modifiedAt: Instant = Instant.EPOCH,
    ) = LocalPathLookup(
        lookedUp = LocalPath.build(path),
        fileType = FileType.FILE,
        size = 16L,
        modifiedAt = modifiedAt,
        target = null,
    )

    /**
     * Mimics how EscalatingWalker treats exceptions thrown by `collector.emit(...)`: a failing
     * emit counts as a walk error in the current mode and is not rethrown (escalation continues),
     * while cancellation propagates. Matching runs downstream of a buffer so matcher exceptions
     * must NEVER travel into emit, where this logic would swallow them and skip remaining items.
     */
    private fun escalatingWalkerLike(vararg items: LocalPathLookup): Flow<LocalPathLookup> = flow {
        try {
            items.forEach { emit(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Swallowed, like a walk error that escalation would handle
        }
    }

    @Test fun `matcher exceptions fail the scan instead of being swallowed by the walker`() = runTest {
        val filter = FakeFilter { throw IllegalStateException("filter boom") }
        val s = scanner(filter)

        coEvery { gatewaySwitch.walk(any(), any()) } returns escalatingWalkerLike(
            lookup("/storage/emulated/0/Android/data/com.test.pkg/cache/junk1"),
            lookup("/storage/emulated/0/Android/data/com.test.pkg/cache/junk2"),
        )

        val thrown = shouldThrow<IllegalStateException> {
            s.readAppDirs(mapOf(areaInfo() to listOf(installId)))
        }
        thrown.message shouldBe "filter boom"
    }

    @Test fun `read errors end the search path but keep prior matches and later paths`() = runTest {
        val filter = FakeFilter { deletion(it) }
        val s = scanner(filter)

        val area1 = areaInfo("com.test.pkg")
        val area2 = areaInfo("com.other.pkg")
        val early = lookup("/storage/emulated/0/Android/data/com.test.pkg/cache/early")
        val other = lookup("/storage/emulated/0/Android/data/com.other.pkg/cache/junk")

        coEvery { gatewaySwitch.walk(area1.file, any()) } returns flow {
            emit(early)
            throw ReadException(path = area1.file)
        }
        coEvery { gatewaySwitch.walk(area2.file, any()) } returns flowOf(other)

        val otherInstallId = InstallId(Pkg.Id("com.other.pkg"), UserHandle2(handleId = 0))
        val results = s.readAppDirs(
            mapOf(
                area1 to listOf(installId),
                area2 to listOf(otherInstallId),
            )
        )

        results[installId]!!.map { it.path } shouldBe listOf(early.lookedUp)
        results[otherInstallId]!!.map { it.path } shouldBe listOf(other.lookedUp)
    }

    @Test fun `items newer than the min cache age are skipped`() = runTest {
        val filter = FakeFilter { deletion(it) }
        val s = scanner(filter, minCacheAgeMs = 24 * 60 * 60 * 1000L)

        val oldItem = lookup("/storage/emulated/0/Android/data/com.test.pkg/cache/old", modifiedAt = Instant.EPOCH)
        val newItem = lookup("/storage/emulated/0/Android/data/com.test.pkg/cache/new", modifiedAt = Instant.now())
        coEvery { gatewaySwitch.walk(any(), any()) } returns flowOf(oldItem, newItem)

        val results = s.readAppDirs(mapOf(areaInfo() to listOf(installId)))

        results[installId]!!.map { it.path } shouldBe listOf(oldItem.lookedUp)
    }

    @Test fun `items outside the search path are skipped`() = runTest {
        val filter = FakeFilter { deletion(it) }
        val s = scanner(filter)

        // Simulates a walk that got redirected (e.g. via links) outside the search path
        val stray = lookup("/storage/emulated/0/Download/elsewhere")
        coEvery { gatewaySwitch.walk(any(), any()) } returns flowOf(stray)

        val results = s.readAppDirs(mapOf(areaInfo() to listOf(installId)))

        results.shouldBeEmpty()
    }
}
