package eu.darken.sdmse.analyzer.core.storage

import android.app.usage.StorageStats
import android.content.pm.ApplicationInfo
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.storage.StorageId
import eu.darken.sdmse.common.storage.StorageManager2
import eu.darken.sdmse.common.storage.StorageStatsManager2
import eu.darken.sdmse.common.storage.StorageVolumeX
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.io.File
import java.util.UUID

class AppStorageScannerTest : BaseTest() {

    private val storageManager2 = mockk<StorageManager2>()
    private val statsManager = mockk<StorageStatsManager2>()
    private val gatewaySwitch = mockk<GatewaySwitch>()
    private val currentUser = UserHandle2(handleId = 0)

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

    private val testAppInfo = ApplicationInfo().apply {
        sourceDir = "/data/app/~~abc==/com.test.app-xyz==/base.apk"
        dataDir = "/data/user/0/com.test.app"
        uid = 10100
    }

    private val mockPkg = mockk<Installed>(relaxed = true).apply {
        every { packageName } returns "com.test.app"
        every { userHandle } returns currentUser
        every { applicationInfo } returns testAppInfo
    }

    @BeforeEach
    fun setup() {
        // Default: StorageStats query fails (tests work without Android StorageStats)
        coEvery { statsManager.queryStatsForPkg(any(), any()) } throws SecurityException("Test")
    }

    private fun createScanner(
        useRoot: Boolean = false,
        useAdb: Boolean = false,
        dataAreas: Set<DataArea> = emptySet(),
    ) = AppStorageScanner(
        storageManager2 = storageManager2,
        statsManager = statsManager,
        gatewaySwitch = gatewaySwitch,
        useRoot = useRoot,
        useAdb = useAdb,
        currentUser = currentUser,
        dataAreas = dataAreas,
        storage = storage,
    )

    @Test
    fun `publicPaths resolves from storageVolumes for primary storage`() = runTest {
        val mockVolume = mockk<StorageVolumeX>().apply {
            every { uuid } returns null  // Primary has null uuid
            every { directory } returns File("/storage/emulated/0")
        }
        every { storageManager2.storageVolumes } returns listOf(mockVolume)

        // Android/data must exist for it to be included
        coEvery { gatewaySwitch.exists(any(), type = any()) } returns false
        coEvery {
            gatewaySwitch.exists(
                match { it.path.contains("Android/data") },
                type = any(),
            )
        } returns true

        val scanner = createScanner()
        val result = scanner.process(
            AppStorageScanner.Request.Initial(
                pkg = mockPkg,
                extraData = emptySet(),
            )
        )

        // With no StorageStats, sizes will be null/0, but the structure should have appData
        result.pkgStat.appData.shouldNotBeNull()
    }

    @Test
    fun `publicPaths is empty when no volume matches`() = runTest {
        val mockVolume = mockk<StorageVolumeX>().apply {
            every { uuid } returns "non-matching-uuid"
            every { directory } returns File("/storage/sdcard1")
        }
        every { storageManager2.storageVolumes } returns listOf(mockVolume)

        coEvery { gatewaySwitch.exists(any(), type = any()) } returns false

        val scanner = createScanner()
        val result = scanner.process(
            AppStorageScanner.Request.Initial(
                pkg = mockPkg,
                extraData = emptySet(),
            )
        )

        // appData still exists (from private data fallback) but with no Android/data entries
        val dataPaths = result.pkgStat.appData?.contents?.map { it.path.path } ?: emptyList()
        dataPaths.none { it.contains("Android/data") } shouldBe true
    }

    @Test
    fun `shallow scan uses StorageStats for data size`() = runTest {
        val mockVolume = mockk<StorageVolumeX>().apply {
            every { uuid } returns null
            every { directory } returns File("/storage/emulated/0")
        }
        every { storageManager2.storageVolumes } returns listOf(mockVolume)

        val mockStats = mockk<StorageStats>().apply {
            every { appBytes } returns 100_000_000L  // 100 MB
            every { dataBytes } returns 30_000_000_000L  // 30 GB
            every { cacheBytes } returns 500_000L
        }
        coEvery { statsManager.queryStatsForPkg(any(), any()) } returns mockStats

        coEvery { gatewaySwitch.exists(any(), type = any()) } returns false

        val scanner = createScanner()
        val result = scanner.process(
            AppStorageScanner.Request.Initial(
                pkg = mockPkg,
                extraData = emptySet(),
            )
        )

        result.pkgStat.isShallow shouldBe true
        // App code should use appBytes
        result.pkgStat.appCode.shouldNotBeNull()
        result.pkgStat.appCode!!.groupSize shouldBe 100_000_000L
        // App data should use dataBytes (StorageStats fallback)
        result.pkgStat.appData.shouldNotBeNull()
        result.pkgStat.appData!!.groupSize shouldBe 30_000_000_000L
        // Total should be appBytes + dataBytes
        result.pkgStat.totalSize shouldBe 30_100_000_000L
    }

    @Test
    fun `shallow scan without matching volume still uses StorageStats fallback`() = runTest {
        // Simulate the old broken behavior: no matching public volume
        every { storageManager2.storageVolumes } returns emptyList()

        val mockStats = mockk<StorageStats>().apply {
            every { appBytes } returns 100_000_000L
            every { dataBytes } returns 30_000_000_000L
            every { cacheBytes } returns 500_000L
        }
        coEvery { statsManager.queryStatsForPkg(any(), any()) } returns mockStats

        coEvery { gatewaySwitch.exists(any(), type = any()) } returns false

        val scanner = createScanner()
        val result = scanner.process(
            AppStorageScanner.Request.Initial(
                pkg = mockPkg,
                extraData = emptySet(),
            )
        )

        // Even without public volume, shallow scan uses StorageStats
        result.pkgStat.appCode.shouldNotBeNull()
        result.pkgStat.appCode!!.groupSize shouldBe 100_000_000L
        result.pkgStat.appData.shouldNotBeNull()
        result.pkgStat.appData!!.groupSize shouldBe 30_000_000_000L
    }
}
