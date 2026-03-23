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
import eu.darken.sdmse.common.storage.VolumeInfoX
import eu.darken.sdmse.common.user.UserHandle2
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

    private fun mockVolume(
        fsUuid: String? = null,
        isPrivate: Boolean = false,
        mountUserId: Int? = 0,
        path: File = File("/storage/emulated"),
        pathForUser: File? = File("/storage/emulated/0"),
    ) = mockk<VolumeInfoX>().apply {
        every { this@apply.fsUuid } returns fsUuid
        every { this@apply.isPrivate } returns isPrivate
        every { this@apply.mountUserId } returns mountUserId
        every { this@apply.path } returns path
        every { this@apply.getPathForUser(any()) } returns pathForUser
        every { this@apply.isMounted } returns true
    }

    @BeforeEach
    fun setup() {
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
    fun `single matching volume resolves publicPaths`() = runTest {
        every { storageManager2.volumes } returns listOf(
            mockVolume(fsUuid = null, mountUserId = 0),
        )

        val mockStats = mockk<StorageStats>().apply {
            every { appBytes } returns 50_000_000L
            every { dataBytes } returns 1_000_000_000L
            every { cacheBytes } returns 100_000L
        }
        coEvery { statsManager.queryStatsForPkg(any(), any()) } returns mockStats
        coEvery { gatewaySwitch.exists(any(), type = any()) } returns false

        val scanner = createScanner()
        val result = scanner.process(
            AppStorageScanner.Request.Initial(pkg = mockPkg, extraData = emptySet())
        )

        // publicPaths resolved → appData should contain an Android/data entry (even if inaccessible)
        result.pkgStat.appData.shouldNotBeNull()
        val dataPaths = result.pkgStat.appData!!.contents.map { it.path.path }
        dataPaths.any { it.contains("/data/user") } shouldBe true
        result.pkgStat.totalSize shouldBe 1_050_000_000L
    }

    @Test
    fun `multiple volumes with different mountUserId picks correct user`() = runTest {
        // The bug scenario: two emulated volumes with fsUuid=null, different mountUserId
        // Old code used singleOrNull which returned null for 2+ matches
        every { storageManager2.volumes } returns listOf(
            mockVolume(isPrivate = true, path = File("/data")),  // private, filtered out
            mockVolume(fsUuid = null, mountUserId = 0, path = File("/storage/emulated")),  // user 0
            mockVolume(fsUuid = null, mountUserId = 10, path = File("/storage/emulated")), // user 10
        )

        val mockStats = mockk<StorageStats>().apply {
            every { appBytes } returns 50_000_000L
            every { dataBytes } returns 1_000_000_000L
            every { cacheBytes } returns 100_000L
        }
        coEvery { statsManager.queryStatsForPkg(any(), any()) } returns mockStats
        coEvery { gatewaySwitch.exists(any(), type = any()) } returns false

        val scanner = createScanner()  // currentUser = 0
        val result = scanner.process(
            AppStorageScanner.Request.Initial(pkg = mockPkg, extraData = emptySet())
        )

        // Should resolve despite multiple volumes (mountUserId filters to user 0 only)
        result.pkgStat.appData.shouldNotBeNull()
        result.pkgStat.totalSize shouldBe 1_050_000_000L
    }

    @Test
    fun `no matching volume returns empty publicPaths`() = runTest {
        every { storageManager2.volumes } returns listOf(
            mockVolume(fsUuid = "some-sdcard-uuid", mountUserId = 0),
        )
        coEvery { gatewaySwitch.exists(any(), type = any()) } returns false

        val scanner = createScanner()
        val result = scanner.process(
            AppStorageScanner.Request.Initial(pkg = mockPkg, extraData = emptySet())
        )

        // appData still exists (from private data fallback) but no Android/data
        val dataPaths = result.pkgStat.appData?.contents?.map { it.path.path } ?: emptyList()
        dataPaths.none { it.contains("Android/data") } shouldBe true
    }

    @Test
    fun `shallow scan uses StorageStats for sizes`() = runTest {
        every { storageManager2.volumes } returns listOf(
            mockVolume(fsUuid = null, mountUserId = 0),
        )

        val mockStats = mockk<StorageStats>().apply {
            every { appBytes } returns 100_000_000L
            every { dataBytes } returns 30_000_000_000L
            every { cacheBytes } returns 500_000L
        }
        coEvery { statsManager.queryStatsForPkg(any(), any()) } returns mockStats
        coEvery { gatewaySwitch.exists(any(), type = any()) } returns false

        val scanner = createScanner()
        val result = scanner.process(
            AppStorageScanner.Request.Initial(pkg = mockPkg, extraData = emptySet())
        )

        result.pkgStat.isShallow shouldBe true
        result.pkgStat.appCode.shouldNotBeNull()
        result.pkgStat.appCode!!.groupSize shouldBe 100_000_000L
        result.pkgStat.appData.shouldNotBeNull()
        result.pkgStat.appData!!.groupSize shouldBe 30_000_000_000L
        result.pkgStat.totalSize shouldBe 30_100_000_000L
    }

    @Test
    fun `volumes API returns null is handled gracefully`() = runTest {
        every { storageManager2.volumes } returns null
        coEvery { gatewaySwitch.exists(any(), type = any()) } returns false

        val scanner = createScanner()
        val result = scanner.process(
            AppStorageScanner.Request.Initial(pkg = mockPkg, extraData = emptySet())
        )

        // Should not crash, just have no Android/data entries
        val dataPaths = result.pkgStat.appData?.contents?.map { it.path.path } ?: emptyList()
        dataPaths.none { it.contains("Android/data") } shouldBe true
    }
}
