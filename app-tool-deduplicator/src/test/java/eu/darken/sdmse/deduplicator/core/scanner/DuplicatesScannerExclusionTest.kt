package eu.darken.sdmse.deduplicator.core.scanner

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathGateway
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.deduplicator.core.arbiter.DuplicatesArbiter
import eu.darken.sdmse.deduplicator.core.scanner.checksum.ChecksumSleuth
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.ExclusionHolder
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import eu.darken.sdmse.exclusion.core.types.UserExclusion
import io.kotest.matchers.collections.shouldContainExactly
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toSet
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import java.time.Instant
import javax.inject.Provider

/**
 * The scan-side counterpart to the exclusion tests the other tools have: a [PathExclusion] tagged
 * [Exclusion.Tag.DEDUPLICATOR] has to keep the matching path out of the candidate set that
 * [DuplicatesScanner] hands to its sleuths.
 *
 * The gateway is stubbed to honour the walk filter the scanner installs, which is where the
 * exclusion check lives, and the checksum sleuth captures what survived it.
 */
class DuplicatesScannerExclusionTest : BaseTest() {

    private val exclusionManager: ExclusionManager = mockk()
    private val areaManager: DataAreaManager = mockk()
    private val gatewaySwitch: GatewaySwitch = mockk()
    private val commonFilesCheck: CommonFilesCheck = mockk(relaxed = true)
    private val fileForensics: FileForensics = mockk(relaxed = true)
    private val arbiter: DuplicatesArbiter = mockk(relaxed = true)
    private val checksumSleuth: ChecksumSleuth = mockk<ChecksumSleuth>(relaxed = true).apply {
        every { progress } returns MutableStateFlow<Progress.Data?>(null)
    }

    private val sdcard = DataArea(
        path = LocalPath.build("/storage/emulated/0"),
        type = DataArea.Type.SDCARD,
        userHandle = UserHandle2(handleId = 0),
    )

    private val keeper = lookup("/storage/emulated/0/photos/keeper.jpg")
    private val excluded = lookup("/storage/emulated/0/excluded/dupe.jpg")

    private fun lookup(path: String) = LocalPathLookup(
        lookedUp = LocalPath.build(path),
        fileType = FileType.FILE,
        size = 16L,
        modifiedAt = Instant.EPOCH,
        target = null,
    )

    private fun scanner(vararg exclusions: Exclusion): DuplicatesScanner {
        val holders: Collection<ExclusionHolder> = exclusions.map { UserExclusion(it) }
        every { exclusionManager.exclusions } returns flowOf(holders)
        every { areaManager.state } returns flowOf(DataAreaManager.State(areas = setOf(sdcard)))

        // The scanner's walk filter is what applies the exclusions, so the stub has to run it.
        coEvery { gatewaySwitch.walk(any(), any()) } answers {
            val options = arg<APathGateway.WalkOptions<APath, APathLookup<APath>>>(1)
            val onFilter = options.onFilter
            listOf(keeper, excluded).asFlow().filter { onFilter == null || onFilter(it) }
        }

        return DuplicatesScanner(
            checksumSleuthProvider = Provider { checksumSleuth },
            pHashSleuthProvider = mockk(),
            mediaHashSleuthProvider = mockk(),
            exclusionManager = exclusionManager,
            areaManager = areaManager,
            dispatcherProvider = TestDispatcherProvider(),
            gatewaySwitch = gatewaySwitch,
            commonFilesCheck = commonFilesCheck,
            fileForensics = fileForensics,
            arbiter = arbiter,
        )
    }

    private fun options() = DuplicatesScanner.Options(
        paths = emptySet(),
        minimumSize = 0L,
        skipUncommon = false,
        useSleuthChecksum = true,
        useSleuthPHash = false,
        useSleuthMedia = false,
    )

    private suspend fun candidatesOf(scanner: DuplicatesScanner): List<String> {
        val candidates = mutableListOf<String>()
        coEvery { checksumSleuth.investigate(any()) } coAnswers {
            firstArg<Flow<APathLookup<*>>>().toSet().forEach { candidates.add(it.path) }
            emptySet()
        }

        scanner.scan(options())

        return candidates
    }

    @Test
    fun `a DEDUPLICATOR path exclusion keeps the matching path out of the scan`() = runTest {
        val scanner = scanner(
            PathExclusion(
                path = LocalPath.build("/storage/emulated/0/excluded"),
                tags = setOf(Exclusion.Tag.DEDUPLICATOR),
            ),
        )

        candidatesOf(scanner) shouldContainExactly listOf(keeper.path)
    }

    @Test
    fun `a path exclusion tagged for another tool does not affect the scan`() = runTest {
        val scanner = scanner(
            PathExclusion(
                path = LocalPath.build("/storage/emulated/0/excluded"),
                tags = setOf(Exclusion.Tag.SYSTEMCLEANER),
            ),
        )

        candidatesOf(scanner).sorted() shouldContainExactly listOf(excluded.path, keeper.path).sorted()
    }

    @Test
    fun `without exclusions both paths are candidates`() = runTest {
        candidatesOf(scanner()).sorted() shouldContainExactly listOf(excluded.path, keeper.path).sorted()
    }
}
