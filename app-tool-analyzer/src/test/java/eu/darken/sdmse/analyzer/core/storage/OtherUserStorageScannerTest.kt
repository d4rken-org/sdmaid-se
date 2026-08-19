package eu.darken.sdmse.analyzer.core.storage

import android.app.usage.StorageStats
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathGateway
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.ReadException
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.storage.StorageId
import eu.darken.sdmse.common.storage.StorageStatsManager2
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.common.user.UserProfile2
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant
import java.util.UUID

class OtherUserStorageScannerTest : BaseTest() {

    private val gatewaySwitch = mockk<GatewaySwitch>()
    private val statsManager = mockk<StorageStatsManager2>()

    private val storageId = StorageId(internalId = null, externalId = UUID.randomUUID())
    private val storage = DeviceStorage(
        id = storageId,
        label = "Primary storage".toCaString(),
        type = DeviceStorage.Type.PRIMARY,
        hardware = DeviceStorage.Hardware.BUILT_IN,
        spaceCapacity = 256_000_000_000L,
        spaceFree = 100_000_000_000L,
        setupIncomplete = false,
    )

    private val user = UserProfile2(handle = UserHandle2(handleId = 10), label = "Second user")

    private val ceArea = DataArea(
        path = LocalPath.build("data_mirror", "data_ce", "null", "10"),
        type = DataArea.Type.PRIVATE_DATA,
        userHandle = user.handle,
    )
    private val deArea = DataArea(
        path = LocalPath.build("data_mirror", "data_de", "null", "10"),
        type = DataArea.Type.PRIVATE_DATA,
        userHandle = user.handle,
    )
    private val mediaPath = LocalPath.build("data", "media", "10")

    private fun dirLookup(path: LocalPath) = LocalPathLookup(
        lookedUp = path,
        fileType = FileType.DIRECTORY,
        size = 4096L,
        modifiedAt = Instant.EPOCH,
        target = null,
    )

    private fun fileLookup(path: LocalPath, size: Long) = LocalPathLookup(
        lookedUp = path,
        fileType = FileType.FILE,
        size = size,
        modifiedAt = Instant.EPOCH,
        target = null,
    )

    /** A walked directory contributes its own 4096 plus the sizes of its children. */
    private fun stubWalk(path: LocalPath, childSize: Long) {
        coEvery { gatewaySwitch.lookup(path, type = any()) } returns dirLookup(path)
        coEvery { gatewaySwitch.walk(path, any()) } returns flowOf(
            fileLookup(path.child("payload.bin"), childSize),
        )
    }

    private fun stubWalkFailure(path: LocalPath) {
        coEvery { gatewaySwitch.lookup(path, type = any()) } returns dirLookup(path)
        coEvery { gatewaySwitch.walk(path, any()) } returns flow { throw ReadException(path = path) }
    }

    /**
     * A walk that emits part of the tree and then fails on a descendant, like the real walkers do:
     * the error goes to [APathGateway.WalkOptions.onError] and is only rethrown when the handler
     * declines to continue. With the default handler (keep going) the walk completes on a partial
     * tree.
     */
    private fun stubWalkDescendantFailure(path: LocalPath, childSize: Long) {
        coEvery { gatewaySwitch.lookup(path, type = any()) } returns dirLookup(path)
        coEvery { gatewaySwitch.walk(path, any()) } answers {
            val options = secondArg<APathGateway.WalkOptions<APath, APathLookup<APath>>>()
            flow {
                emit(fileLookup(path.child("payload.bin"), childSize))
                val subDir = dirLookup(path.child("subdir"))
                val error = ReadException(path = subDir.lookedUp)
                if (options.onError?.invoke(subDir, error) == false) throw error
            }
        }
    }

    private fun stubWalkOverItemLimit(path: LocalPath) {
        coEvery { gatewaySwitch.lookup(path, type = any()) } returns dirLookup(path)
        coEvery { gatewaySwitch.walk(path, any()) } returns flow {
            // One past the scanner's 100_000 item ceiling.
            repeat(100_001) { emit(fileLookup(path.child("payload-$it.bin"), 1L)) }
        }
    }

    private fun stubStats(dataBytes: Long, cacheBytes: Long) {
        val stats = mockk<StorageStats>().apply {
            every { this@apply.dataBytes } returns dataBytes
            every { this@apply.cacheBytes } returns cacheBytes
            every { appBytes } returns 999_999L
        }
        coEvery { statsManager.queryStatsForUser(storageId, user.handle) } returns stats
    }

    @BeforeEach
    fun setup() {
        coEvery { statsManager.queryStatsForUser(any(), any()) } throws SecurityException("Test")
        coEvery { gatewaySwitch.du(any(), any()) } throws ReadException("Test")
    }

    private val scanner: OtherUserStorageScanner
        get() = OtherUserStorageScanner(gatewaySwitch = gatewaySwitch, statsManager = statsManager)

    private suspend fun scan(
        useRoot: Boolean,
        dataAreas: Set<DataArea> = emptySet(),
        users: Collection<UserProfile2> = setOf(user),
    ) = scanner.scan(
        storage = storage,
        users = users,
        dataAreas = dataAreas,
        useRoot = useRoot,
    )

    @Test
    fun `no other users means no category`() = runTest {
        scan(useRoot = true, users = emptySet()).shouldBeNull()
    }

    @Test
    fun `stats tier counts dataBytes only, never dataBytes plus cacheBytes`() = runTest {
        // dataBytes already includes cacheBytes, adding them would over-report by the whole cache.
        stubStats(dataBytes = 5_000_000L, cacheBytes = 1_500_000L)

        val category = scan(useRoot = false).shouldNotBeNull()

        category.spaceUsed shouldBe 5_000_000L
        category.groups.single().groupSize shouldBe 5_000_000L
    }

    @Test
    fun `stats tier does not claim shared media and is not browsable`() = runTest {
        stubStats(dataBytes = 5_000_000L, cacheBytes = 1_500_000L)

        val entry = scan(useRoot = false).shouldNotBeNull().users.single()

        entry.handle shouldBe user.handle
        entry.appDataKnown shouldBe true
        entry.sharedMediaKnown shouldBe false
        entry.isBrowsable shouldBe false
    }

    @Test
    fun `stats tier never walks or sizes another user's public storage`() = runTest {
        // Below root, `du` on /storage/emulated/<id> silently reports a few KB for a real tree.
        stubStats(dataBytes = 5_000_000L, cacheBytes = 0L)

        scan(useRoot = false).shouldNotBeNull()

        coVerify(exactly = 0) { gatewaySwitch.walk(any(), any()) }
        coVerify(exactly = 0) { gatewaySwitch.du(any(), any()) }
        coVerify(exactly = 0) { gatewaySwitch.lookup(any(), type = any()) }
    }

    @Test
    fun `a complete root walk covers app data and shared media`() = runTest {
        stubWalk(ceArea.path as LocalPath, childSize = 1_000L)
        stubWalk(deArea.path as LocalPath, childSize = 2_000L)
        stubWalk(mediaPath, childSize = 3_000L)

        val category = scan(useRoot = true, dataAreas = setOf(ceArea, deArea)).shouldNotBeNull()

        // Each of the three walked dirs contributes its own 4096 plus its child.
        category.spaceUsed shouldBe (3 * 4096L + 1_000L + 2_000L + 3_000L)
        val entry = category.users.single()
        entry.appDataKnown shouldBe true
        entry.sharedMediaKnown shouldBe true
        entry.isBrowsable shouldBe true
    }

    @Test
    fun `a partial root failure falls back to stats and discards the partial sizes`() = runTest {
        // The private-data walks succeed, the media walk doesn't. Keeping the partial root sizes and
        // adding stats on top would double-count: dataBytes covers /data/media/<id>/Android too.
        stubWalk(ceArea.path as LocalPath, childSize = 1_000L)
        stubWalk(deArea.path as LocalPath, childSize = 2_000L)
        stubWalkFailure(mediaPath)
        stubStats(dataBytes = 500_000L, cacheBytes = 10_000L)

        val category = scan(useRoot = true, dataAreas = setOf(ceArea, deArea)).shouldNotBeNull()

        category.spaceUsed shouldBe 500_000L
        category.groups.single().contents.single().path shouldBe LocalPath.build("data", "user", "10")
        val entry = category.users.single()
        entry.appDataKnown shouldBe true
        entry.sharedMediaKnown shouldBe false
        entry.isBrowsable shouldBe false
    }

    @Test
    fun `a descendant failure mid-walk falls back to stats, not a partial tree`() = runTest {
        // An app writing or deleting inside a directory while it is being walked. Continuing past
        // the error would finish with a partial tree and report the user as fully known.
        stubWalk(ceArea.path as LocalPath, childSize = 1_000L)
        stubWalk(deArea.path as LocalPath, childSize = 2_000L)
        stubWalkDescendantFailure(mediaPath, childSize = 3_000L)
        stubStats(dataBytes = 500_000L, cacheBytes = 10_000L)

        val category = scan(useRoot = true, dataAreas = setOf(ceArea, deArea)).shouldNotBeNull()

        category.spaceUsed shouldBe 500_000L
        category.groups.single().contents.single().path shouldBe LocalPath.build("data", "user", "10")
        val entry = category.users.single()
        entry.appDataKnown shouldBe true
        entry.sharedMediaKnown shouldBe false
        entry.isBrowsable shouldBe false
    }

    @Test
    fun `exceeding the walk item limit falls back to stats instead of sizing`() = runTest {
        stubWalk(ceArea.path as LocalPath, childSize = 1_000L)
        stubWalk(deArea.path as LocalPath, childSize = 2_000L)
        stubWalkOverItemLimit(mediaPath)
        stubStats(dataBytes = 500_000L, cacheBytes = 10_000L)

        val category = scan(useRoot = true, dataAreas = setOf(ceArea, deArea)).shouldNotBeNull()

        category.spaceUsed shouldBe 500_000L
        category.groups.single().contents.single().path shouldBe LocalPath.build("data", "user", "10")
        category.users.single().sharedMediaKnown shouldBe false
        // `du` isn't atomic either, a truncated walk must not be papered over with a size.
        coVerify(exactly = 0) { gatewaySwitch.du(any(), any()) }
    }

    @Test
    fun `a locked credential encrypted area leaves app data unknown`() = runTest {
        // PrivateDataModule silently omits locked CE areas, so only the DE half shows up.
        stubWalk(deArea.path as LocalPath, childSize = 2_000L)
        stubWalk(mediaPath, childSize = 3_000L)

        val category = scan(useRoot = true, dataAreas = setOf(deArea)).shouldNotBeNull()

        category.spaceUsed shouldBe 0L
        category.groups.single().contents.shouldBeEmpty()
        val entry = category.users.single()
        entry.appDataKnown shouldBe false
        entry.sharedMediaKnown shouldBe false
        entry.isBrowsable shouldBe false
    }

    @Test
    fun `a failed root walk does not present as a small success`() = runTest {
        // walkContentItem swallows ReadException and returns the bare directory, which would show
        // this user as a 4 KB user. Without stats there must be no size at all.
        stubWalkFailure(ceArea.path as LocalPath)
        stubWalk(deArea.path as LocalPath, childSize = 2_000L)
        stubWalk(mediaPath, childSize = 3_000L)

        val category = scan(useRoot = true, dataAreas = setOf(ceArea, deArea)).shouldNotBeNull()

        category.spaceUsed shouldBe 0L
        category.users.single().appDataKnown shouldBe false
    }
}
