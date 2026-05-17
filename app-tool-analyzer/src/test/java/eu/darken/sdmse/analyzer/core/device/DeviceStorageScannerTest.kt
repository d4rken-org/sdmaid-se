package eu.darken.sdmse.analyzer.core.device

import eu.darken.sdmse.common.storage.StorageEnvironment
import eu.darken.sdmse.common.storage.StorageId
import eu.darken.sdmse.common.storage.StorageManager2
import eu.darken.sdmse.common.storage.StorageStatsManager2
import eu.darken.sdmse.common.storage.VolumeInfoX
import eu.darken.sdmse.setup.SetupModule
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.io.File

class DeviceStorageScannerTest : BaseTest() {

    private val setupModule = mockk<SetupModule>()
    private val environment = mockk<StorageEnvironment>(relaxed = true)
    private val storageManager2 = mockk<StorageManager2>()
    private val statsManager = mockk<StorageStatsManager2>()

    private fun mockVolume(
        fsUuid: String?,
        path: File? = mockFile(totalSpace = 128_000_000_000L, freeSpace = 64_000_000_000L),
        isUsb: Boolean = false,
    ) = mockk<VolumeInfoX>().apply {
        every { this@apply.fsUuid } returns fsUuid
        every { this@apply.isPrimary } returns false
        every { this@apply.isMounted } returns true
        every { this@apply.path } returns path
        every { this@apply.disk } returns mockk {
            every { this@mockk.isUsb } returns isUsb
        }
    }

    private fun mockFile(totalSpace: Long, freeSpace: Long): File = mockk<File>().apply {
        every { this@apply.totalSpace } returns totalSpace
        every { this@apply.freeSpace } returns freeSpace
        every { this@apply.path } returns "/storage/mocked"
    }

    @BeforeEach
    fun setup() {
        val completeState = mockk<SetupModule.State.Current> {
            every { isComplete } returns true
            every { type } returns SetupModule.Type.STORAGE
        }
        every { setupModule.state } returns flowOf(completeState)
        // Primary block: let stats succeed so environment.dataDir fallback is not exercised.
        coEvery { statsManager.getTotalBytes(match { it.internalId == null }) } returns 128_000_000_000L
        coEvery { statsManager.getFreeBytes(match { it.internalId == null }) } returns 64_000_000_000L
    }

    private fun createScanner() = DeviceStorageScanner(
        storageSetupModule = setupModule,
        environment = environment,
        storageManager2 = storageManager2,
        storageStatsmanager = statsManager,
    )

    private fun Set<DeviceStorage>.secondary() = firstOrNull { it.type == DeviceStorage.Type.SECONDARY }

    @Test
    fun `stats succeed on non-FAT UUID uses stats values`() = runTest {
        val fsUuid = "12345678-1234-1234-1234-123456789abc"
        every { storageManager2.volumes } returns listOf(mockVolume(fsUuid = fsUuid))
        coEvery { statsManager.getTotalBytes(match { it.internalId == fsUuid }) } returns 256_000_000_000L
        coEvery { statsManager.getFreeBytes(match { it.internalId == fsUuid }) } returns 100_000_000_000L

        val secondary = createScanner().scan().secondary()

        secondary shouldNotBe null
        secondary!!.spaceCapacity shouldBe 256_000_000_000L
        secondary.spaceFree shouldBe 100_000_000_000L
    }

    @Test
    fun `getFreeBytes throws falls back to File for both`() = runTest {
        val fsUuid = "12345678-1234-1234-1234-123456789abc"
        val path = mockFile(totalSpace = 128_000_000_000L, freeSpace = 4_300_000_000L)
        every { storageManager2.volumes } returns listOf(mockVolume(fsUuid = fsUuid, path = path))
        coEvery { statsManager.getTotalBytes(match { it.internalId == fsUuid }) } returns 256_000_000_000L
        coEvery { statsManager.getFreeBytes(match { it.internalId == fsUuid }) } throws IllegalStateException("sentinel")

        val secondary = createScanner().scan().secondary()

        secondary!!.spaceCapacity shouldBe 128_000_000_000L
        secondary.spaceFree shouldBe 4_300_000_000L
    }

    @Test
    fun `getTotalBytes throws falls back to File for both`() = runTest {
        val fsUuid = "12345678-1234-1234-1234-123456789abc"
        val path = mockFile(totalSpace = 128_000_000_000L, freeSpace = 4_300_000_000L)
        every { storageManager2.volumes } returns listOf(mockVolume(fsUuid = fsUuid, path = path))
        coEvery { statsManager.getTotalBytes(match { it.internalId == fsUuid }) } throws IllegalStateException("sentinel")
        coEvery { statsManager.getFreeBytes(match { it.internalId == fsUuid }) } returns 50_000_000_000L

        val secondary = createScanner().scan().secondary()

        secondary!!.spaceCapacity shouldBe 128_000_000_000L
        secondary.spaceFree shouldBe 4_300_000_000L
    }

    @Test
    fun `FAT UUID with big mismatch prefers File (reproduces #2389)`() = runTest {
        // Reporter's SD card: 128GB FAT, API says 256GB (2x wrong)
        val fsUuid = "EFFD-F4D5"
        val path = mockFile(totalSpace = 128_000_000_000L, freeSpace = 4_658_036_736L)
        every { storageManager2.volumes } returns listOf(mockVolume(fsUuid = fsUuid, path = path))
        // Both stats calls succeed with wrong-but-non-sentinel values (hypothetical — in #2389 getFreeBytes
        // actually threw; this covers the case where it wouldn't have, so pair-coupling alone wouldn't help).
        coEvery { statsManager.getTotalBytes(match { it.internalId == fsUuid }) } returns 256_000_000_000L
        coEvery { statsManager.getFreeBytes(match { it.internalId == fsUuid }) } returns 4_658_036_736L

        val secondary = createScanner().scan().secondary()

        secondary!!.spaceCapacity shouldBe 128_000_000_000L
        secondary.spaceFree shouldBe 4_658_036_736L
    }

    @Test
    fun `FAT UUID with small mismatch keeps stats values`() = runTest {
        // Within the 10% tolerance — don't second-guess the API.
        val fsUuid = "EFFD-F4D5"
        val path = mockFile(totalSpace = 128_000_000_000L, freeSpace = 50_000_000_000L)
        every { storageManager2.volumes } returns listOf(mockVolume(fsUuid = fsUuid, path = path))
        coEvery { statsManager.getTotalBytes(match { it.internalId == fsUuid }) } returns 130_000_000_000L
        coEvery { statsManager.getFreeBytes(match { it.internalId == fsUuid }) } returns 52_000_000_000L

        val secondary = createScanner().scan().secondary()

        secondary!!.spaceCapacity shouldBe 130_000_000_000L
        secondary.spaceFree shouldBe 52_000_000_000L
    }

    @Test
    fun `zero capacity from all sources drops the volume`() = runTest {
        val fsUuid = "12345678-1234-1234-1234-123456789abc"
        every { storageManager2.volumes } returns listOf(mockVolume(fsUuid = fsUuid, path = null))
        coEvery { statsManager.getTotalBytes(match { it.internalId == fsUuid }) } throws IllegalStateException("sentinel")
        coEvery { statsManager.getFreeBytes(match { it.internalId == fsUuid }) } throws IllegalStateException("sentinel")

        val result = createScanner().scan()

        result.secondary() shouldBe null
    }

    @Test
    fun `FAT prefix constant matches synthesised UUIDs`() {
        // Sanity check that the isFatUuid detection key is correct.
        val synthesised = StorageId.parseVolumeUuid("EFFD-F4D5")
        synthesised!!.toString().startsWith(StorageId.FAT_UUID_PREFIX) shouldBe true
    }

    @Test
    fun `exotic 16-hex fsUuid is accepted and falls back to File API (reproduces #2418)`() = runTest {
        // Huawei Mate 40 Pro / Android 12 / NTFS-style SD card serial.
        val fsUuid = "1C32C2D032C2AE58"
        val path = mockFile(totalSpace = 250_000_000_000L, freeSpace = 100_000_000_000L)
        every { storageManager2.volumes } returns listOf(mockVolume(fsUuid = fsUuid, path = path))
        // System doesn't know the synthesised UUID, so stats calls fail and File API is used.
        coEvery { statsManager.getTotalBytes(match { it.internalId == fsUuid }) } throws IllegalStateException("unknown volume")
        coEvery { statsManager.getFreeBytes(match { it.internalId == fsUuid }) } throws IllegalStateException("unknown volume")

        val secondary = createScanner().scan().secondary()

        secondary shouldNotBe null
        secondary!!.spaceCapacity shouldBe 250_000_000_000L
        secondary.spaceFree shouldBe 100_000_000_000L
    }
}
