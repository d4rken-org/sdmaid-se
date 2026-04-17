package eu.darken.sdmse.setup.saf

import android.content.ContentResolver
import android.content.Intent
import android.content.UriPermission
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.R
import eu.darken.sdmse.common.BuildWrap
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.device.DeviceDetective
import eu.darken.sdmse.common.device.RomType
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.saf.SAFPath
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
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
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterIsInstance
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
