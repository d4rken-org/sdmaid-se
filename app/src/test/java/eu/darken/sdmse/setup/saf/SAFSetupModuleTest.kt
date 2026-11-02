package eu.darken.sdmse.setup.saf

import android.content.ContentResolver
import android.content.Intent
import android.content.UriPermission
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.R
import eu.darken.sdmse.common.BuildWrap
import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.device.DeviceDetective
import eu.darken.sdmse.common.device.RomType
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.saf.SAFPath
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.storage.PathMapper
import eu.darken.sdmse.common.storage.StorageEnvironment
import eu.darken.sdmse.common.storage.StorageManager2
import eu.darken.sdmse.common.storage.StorageVolumeX
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.common.user.UserManager2
import eu.darken.sdmse.common.user.UserProfile2
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.TestApplication

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class SAFSetupModuleTest {

    private lateinit var contentResolver: ContentResolver
    private lateinit var storageManager2: StorageManager2
    private lateinit var storageEnvironment: StorageEnvironment
    private lateinit var pathMapper: PathMapper
    private lateinit var dataAreaManager: DataAreaManager
    private lateinit var gatewaySwitch: GatewaySwitch
    private lateinit var deviceDetective: DeviceDetective
    private lateinit var pkgOps: PkgOps
    private lateinit var userManager: UserManager2
    private lateinit var rootManager: RootManager
    private lateinit var adbManager: AdbManager

    private val currentUser = UserProfile2(handle = UserHandle2(handleId = 0))
    private val context
        get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setup() {
        contentResolver = mockk(relaxed = true)
        storageManager2 = mockk(relaxed = true)
        storageEnvironment = mockk(relaxed = true)
        pathMapper = mockk(relaxed = true)
        dataAreaManager = mockk(relaxed = true)
        gatewaySwitch = mockk(relaxed = true)
        deviceDetective = mockk(relaxed = true)
        pkgOps = mockk(relaxed = true)
        userManager = mockk(relaxed = true)
        rootManager = mockk(relaxed = true)
        adbManager = mockk(relaxed = true)

        mockkObject(BuildWrap)
        mockkObject(BuildWrap.VERSION)

        every { BuildWrap.VERSION.CODENAME } returns "REL"
        every { deviceDetective.getROMType() } returns RomType.AOSP
        every { contentResolver.persistedUriPermissions } returns emptyList()
        every { storageManager2.storageVolumes } returns emptyList()
        every { storageEnvironment.externalDirs } returns emptyList()
        coEvery { pathMapper.toSAFPath(any()) } returns null
        coEvery { gatewaySwitch.exists(any()) } returns false
        coEvery { userManager.allUsers() } returns setOf(currentUser)
        coEvery { userManager.currentUser() } returns currentUser
        coEvery { pkgOps.queryPkg(any(), any(), any(), any()) } returns null
        every { rootManager.useRoot } returns flowOf(false)
        every { adbManager.useAdb } returns flowOf(false)
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `Android TV skips SAF setup completely`() = runTest {
        every { BuildWrap.VERSION.SDK_INT } returns 31
        every { deviceDetective.getROMType() } returns RomType.ANDROID_TV
        every { storageManager2.storageVolumes } returns listOf(
            volume(directory = File("/storage/emulated/0"), isMounted = true),
        )

        val result = newModule(backgroundScope).awaitCurrent()

        result.paths shouldBe emptyList()
    }

    @Test
    fun `below API 30 uses mounted mapped volumes and matches persisted permissions`() = runTest {
        every { BuildWrap.VERSION.SDK_INT } returns 29

        val primaryDir = File("/storage/emulated/0")
        val sdDir = File("/storage/ABCD-EF12")
        val primaryPath = SAFPath.build("content://com.android.externalstorage.documents/tree/primary%3A")
        val sdPath = SAFPath.build("content://com.android.externalstorage.documents/tree/ABCD-EF12%3A")

        every { storageManager2.storageVolumes } returns listOf(
            volume(directory = primaryDir, isMounted = true, isRemovable = false),
            volume(directory = sdDir, isMounted = true, isRemovable = true),
            volume(directory = File("/storage/unmounted"), isMounted = false),
            volume(directory = null, isMounted = true),
        )
        coEvery { pathMapper.toSAFPath(LocalPath.build(primaryDir)) } returns primaryPath
        coEvery { pathMapper.toSAFPath(LocalPath.build(sdDir)) } returns sdPath
        every {
            contentResolver.persistedUriPermissions
        } returns listOf(uriPermissionFor(primaryPath.pathUri))

        val result = newModule(backgroundScope).awaitCurrent()

        result.paths shouldHaveSize 2
        result.paths.map { it.label.get(context) } shouldContainExactly listOf(
            context.getString(R.string.data_area_public_storage_label),
            context.getString(R.string.data_area_sdcard_label),
        )
        result.paths.map { it.hasAccess } shouldContainExactly listOf(true, false)
    }

    @Test
    fun `below API 30 skips unmappable mounted volumes`() = runTest {
        every { BuildWrap.VERSION.SDK_INT } returns 29
        every { storageManager2.storageVolumes } returns listOf(
            volume(directory = File("/storage/emulated/0"), isMounted = true),
        )
        coEvery { pathMapper.toSAFPath(any()) } returns null

        val result = newModule(backgroundScope).awaitCurrent()

        result.paths shouldBe emptyList()
    }

    @Test
    fun `API 30 to 32 adds Android data and obb targets when DocumentsUI is unrestricted`() = runTest {
        every { BuildWrap.VERSION.SDK_INT } returns 31

        val baseDir = LocalPath.build(File("/storage/emulated/0"))
        val dataDir = baseDir.child("Android", "data")
        val obbDir = baseDir.child("Android", "obb")
        val dataSaf = SAFPath.build(
            "content://com.android.externalstorage.documents/tree/primary",
            "Android",
            "data",
        )
        val obbSaf = SAFPath.build(
            "content://com.android.externalstorage.documents/tree/primary",
            "Android",
            "obb",
        )

        every { storageEnvironment.externalDirs } returns listOf(baseDir)
        coEvery { gatewaySwitch.exists(dataDir) } returns true
        coEvery { gatewaySwitch.exists(obbDir) } returns true
        coEvery { pathMapper.toSAFPath(dataDir) } returns dataSaf
        coEvery { pathMapper.toSAFPath(obbDir) } returns obbSaf
        coEvery { pkgOps.queryPkg(any(), any(), any(), any()) } returns documentsUiPkg(
            targetSdkVersion = 33,
            longVersionCode = 1L,
        )

        val result = newModule(backgroundScope).awaitCurrent()

        result.paths shouldHaveSize 2
        result.paths.map { it.label.get(context) } shouldContainExactly listOf(
            context.getString(R.string.data_area_public_app_data_official_label),
            context.getString(R.string.data_area_public_app_assets_official_label),
        )
    }

    @Test
    fun `API 30 to 32 suppresses Android data and obb targets when DocumentsUI is restricted`() = runTest {
        every { BuildWrap.VERSION.SDK_INT } returns 31

        val baseDir = LocalPath.build(File("/storage/emulated/0"))
        every { storageEnvironment.externalDirs } returns listOf(baseDir)
        coEvery { gatewaySwitch.exists(any()) } returns true
        coEvery { pkgOps.queryPkg(any(), any(), any(), any()) } returns documentsUiPkg(
            targetSdkVersion = 34,
            longVersionCode = 1L,
        )

        val result = newModule(backgroundScope).awaitCurrent()

        result.paths shouldBe emptyList()
        coVerify(exactly = 0) { pathMapper.toSAFPath(any()) }
    }

    @Test
    fun `API 33 plus skips Android data and obb SAF targets entirely`() = runTest {
        every { BuildWrap.VERSION.SDK_INT } returns 33

        val baseDir = LocalPath.build(File("/storage/emulated/0"))
        every { storageEnvironment.externalDirs } returns listOf(baseDir)
        coEvery { gatewaySwitch.exists(any()) } returns true
        coEvery { pkgOps.queryPkg(any(), any(), any(), any()) } returns documentsUiPkg(
            targetSdkVersion = 33,
            longVersionCode = 1L,
        )

        val result = newModule(backgroundScope).awaitCurrent()

        result.paths shouldBe emptyList()
        coVerify(exactly = 0) { pathMapper.toSAFPath(any()) }
    }

    @Test
    fun `API 30 to 32 skips Android data and obb targets while root is in use`() = runTest {
        every { BuildWrap.VERSION.SDK_INT } returns 31
        setupDataObbTargets()
        every { rootManager.useRoot } returns flowOf(true)

        val result = newModule(backgroundScope).awaitCurrent()

        result.paths shouldBe emptyList()
        result.isComplete shouldBe true
    }

    @Test
    fun `API 30 to 32 skips Android data and obb targets while ADB is in use`() = runTest {
        every { BuildWrap.VERSION.SDK_INT } returns 31
        setupDataObbTargets()
        every { adbManager.useAdb } returns flowOf(true)

        val result = newModule(backgroundScope).awaitCurrent()

        result.paths shouldBe emptyList()
    }

    @Test
    fun `API 30 to 32 restores Android data and obb targets once neither root nor ADB is in use`() = runTest {
        every { BuildWrap.VERSION.SDK_INT } returns 31
        val targets = setupDataObbTargets()
        val useRoot = MutableStateFlow(true)
        val useAdb = MutableStateFlow(true)
        every { rootManager.useRoot } returns useRoot
        every { adbManager.useAdb } returns useAdb

        val moduleScope = foregroundScope()
        try {
            val results = moduleScope.collectResults(newModule(moduleScope))
            advanceUntilIdle()
            results.last().paths shouldBe emptyList()

            useRoot.value = false
            advanceUntilIdle()
            results.last().paths shouldBe emptyList()
            results.all { it.paths.isEmpty() } shouldBe true

            useAdb.value = false
            advanceUntilIdle()
            results.last().paths.map { it.safPath } shouldContainExactly listOf(targets.dataSaf, targets.obbSaf)
            results.last().isComplete shouldBe false
        } finally {
            moduleScope.cancel()
        }
    }

    @Test
    fun `API 30 to 32 drops Android data and obb targets when root or ADB becomes usable without refresh`() = runTest {
        every { BuildWrap.VERSION.SDK_INT } returns 31
        val targets = setupDataObbTargets()
        val useRoot = MutableStateFlow(false)
        val useAdb = MutableStateFlow(false)
        every { rootManager.useRoot } returns useRoot
        every { adbManager.useAdb } returns useAdb

        val moduleScope = foregroundScope()
        try {
            val results = moduleScope.collectResults(newModule(moduleScope))
            advanceUntilIdle()
            results.last().paths.map { it.safPath } shouldContainExactly listOf(targets.dataSaf, targets.obbSaf)

            useAdb.value = true
            advanceUntilIdle()
            results.last().paths shouldBe emptyList()
        } finally {
            moduleScope.cancel()
        }
    }

    @Test
    fun `below API 30 keeps volume targets while root is in use`() = runTest {
        every { BuildWrap.VERSION.SDK_INT } returns 29
        every { rootManager.useRoot } returns flowOf(true)

        val primaryDir = File("/storage/emulated/0")
        val sdDir = File("/storage/ABCD-EF12")
        val primaryPath = SAFPath.build("content://com.android.externalstorage.documents/tree/primary%3A")
        val sdPath = SAFPath.build("content://com.android.externalstorage.documents/tree/ABCD-EF12%3A")

        every { storageManager2.storageVolumes } returns listOf(
            volume(directory = primaryDir, isMounted = true, isRemovable = false),
            volume(directory = sdDir, isMounted = true, isRemovable = true),
        )
        coEvery { pathMapper.toSAFPath(LocalPath.build(primaryDir)) } returns primaryPath
        coEvery { pathMapper.toSAFPath(LocalPath.build(sdDir)) } returns sdPath

        val result = newModule(backgroundScope).awaitCurrent()

        result.paths.map { it.safPath } shouldContainExactly listOf(primaryPath, sdPath)
    }

    @Test
    fun `takePermission accepts Android data while root is in use`() = runTest {
        every { BuildWrap.VERSION.SDK_INT } returns 31
        val targets = setupDataObbTargets()
        every { rootManager.useRoot } returns flowOf(true)

        newModule(backgroundScope).takePermission(targets.dataSaf.pathUri)

        verify { pathMapper.takePermission(targets.dataSaf.pathUri) }
    }

    @Test
    fun `evaluation started before root becomes usable is not published`() = runTest {
        every { BuildWrap.VERSION.SDK_INT } returns 31
        val targets = setupDataObbTargets()
        val existsGate = CompletableDeferred<Boolean>()
        coEvery { gatewaySwitch.exists(targets.dataDir) } coAnswers { existsGate.await() }
        val useRoot = MutableStateFlow(false)
        every { rootManager.useRoot } returns useRoot

        val moduleScope = foregroundScope()
        try {
            val results = moduleScope.collectResults(newModule(moduleScope))
            advanceUntilIdle()
            coVerify { gatewaySwitch.exists(targets.dataDir) }
            results shouldBe emptyList()

            useRoot.value = true
            advanceUntilIdle()

            existsGate.complete(true)
            advanceUntilIdle()

            results.none { it.paths.isNotEmpty() } shouldBe true
            results.last().paths shouldBe emptyList()
        } finally {
            moduleScope.cancel()
        }
    }

    private class DataObbTargets(
        val dataDir: LocalPath,
        val obbDir: LocalPath,
        val dataSaf: SAFPath,
        val obbSaf: SAFPath,
    )

    private fun setupDataObbTargets(): DataObbTargets {
        val baseDir = LocalPath.build(File("/storage/emulated/0"))
        val targets = DataObbTargets(
            dataDir = baseDir.child("Android", "data"),
            obbDir = baseDir.child("Android", "obb"),
            dataSaf = SAFPath.build(
                "content://com.android.externalstorage.documents/tree/primary",
                "Android",
                "data",
            ),
            obbSaf = SAFPath.build(
                "content://com.android.externalstorage.documents/tree/primary",
                "Android",
                "obb",
            ),
        )

        every { storageEnvironment.externalDirs } returns listOf(baseDir)
        coEvery { gatewaySwitch.exists(targets.dataDir) } returns true
        coEvery { gatewaySwitch.exists(targets.obbDir) } returns true
        coEvery { pathMapper.toSAFPath(targets.dataDir) } returns targets.dataSaf
        coEvery { pathMapper.toSAFPath(targets.obbDir) } returns targets.obbSaf
        coEvery { pkgOps.queryPkg(any(), any(), any(), any()) } returns documentsUiPkg(
            targetSdkVersion = 33,
            longVersionCode = 1L,
        )
        return targets
    }

    // advanceUntilIdle() only drives foreground tasks, so work launched in backgroundScope would never run.
    private fun TestScope.foregroundScope(): CoroutineScope = CoroutineScope(coroutineContext + Job())

    private fun CoroutineScope.collectResults(module: SAFSetupModule): List<SAFSetupModule.Result> {
        val results = mutableListOf<SAFSetupModule.Result>()
        launch(start = CoroutineStart.UNDISPATCHED) {
            module.state.filterIsInstance<SAFSetupModule.Result>().toList(results)
        }
        return results
    }

    private fun newModule(appScope: CoroutineScope) = SAFSetupModule(
        appScope = appScope,
        contentResolver = contentResolver,
        storageManager2 = storageManager2,
        storageEnvironment = storageEnvironment,
        pathMapper = pathMapper,
        dataAreaManager = dataAreaManager,
        gatewaySwitch = gatewaySwitch,
        deviceDetective = deviceDetective,
        pkgOps = pkgOps,
        userManager = userManager,
        rootManager = rootManager,
        adbManager = adbManager,
    )

    private suspend fun SAFSetupModule.awaitCurrent(): SAFSetupModule.Result =
        state.filterIsInstance<SAFSetupModule.Result>().first()

    private fun volume(
        directory: File?,
        isMounted: Boolean,
        isRemovable: Boolean = false,
    ): StorageVolumeX = mockk {
        every { this@mockk.directory } returns directory
        every { this@mockk.isMounted } returns isMounted
        every { this@mockk.isRemovable } returns isRemovable
    }

    private fun uriPermissionFor(uri: android.net.Uri): UriPermission = mockk {
        every { this@mockk.uri } returns uri
        every { this@mockk.isReadPermission } returns true
        every { this@mockk.isWritePermission } returns true
    }

    private fun documentsUiPkg(
        targetSdkVersion: Int,
        longVersionCode: Long,
    ): PackageInfo = PackageInfo().apply {
        applicationInfo = ApplicationInfo().apply {
            this.targetSdkVersion = targetSdkVersion
        }
        versionName = "test"
        this.longVersionCode = longVersionCode
    }
}
