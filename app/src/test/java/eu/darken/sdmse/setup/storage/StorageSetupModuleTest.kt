package eu.darken.sdmse.setup.storage

import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.R
import eu.darken.sdmse.common.BuildWrap
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.device.DeviceDetective
import eu.darken.sdmse.common.device.RomType
import eu.darken.sdmse.common.permissions.Permission
import eu.darken.sdmse.common.storage.StorageManager2
import eu.darken.sdmse.common.storage.StorageVolumeX
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
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
class StorageSetupModuleTest {

    private lateinit var storageManager2: StorageManager2
    private lateinit var dataAreaManager: DataAreaManager
    private lateinit var deviceDetective: DeviceDetective

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setup() {
        storageManager2 = mockk(relaxed = true)
        dataAreaManager = mockk(relaxed = true)
        deviceDetective = mockk(relaxed = true)

        mockkObject(BuildWrap)
        mockkObject(BuildWrap.VERSION)
        mockkStatic(ContextCompat::class)
        mockkStatic(Environment::class)

        every { BuildWrap.VERSION.CODENAME } returns "REL"
        every { deviceDetective.getROMType() } returns RomType.AOSP
        every { storageManager2.storageVolumes } returns emptyList()
        every { ContextCompat.checkSelfPermission(context, any()) } returns PackageManager.PERMISSION_DENIED
        every { Environment.isExternalStorageManager() } returns false
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `non TV API 30 plus uses MANAGE_EXTERNAL_STORAGE`() = runTest {
        every { BuildWrap.VERSION.SDK_INT } returns 30
        every { storageManager2.storageVolumes } returns listOf(
            volume(
                directory = File("/storage/emulated/0"),
                isPrimary = true,
                isEmulated = true,
            ),
        )

        val result = newModule(backgroundScope).awaitCurrent()

        result.missingPermission shouldContainExactly setOf(Permission.MANAGE_EXTERNAL_STORAGE)
        result.paths shouldHaveSize 1
        result.paths.single().hasAccess shouldBe false
    }

    @Test
    fun `non TV below API 30 uses READ and WRITE storage permissions`() = runTest {
        every { BuildWrap.VERSION.SDK_INT } returns 29
        every { storageManager2.storageVolumes } returns listOf(
            volume(
                directory = File("/storage/emulated/0"),
                isPrimary = true,
                isEmulated = true,
            ),
        )
        every {
            ContextCompat.checkSelfPermission(context, Permission.READ_EXTERNAL_STORAGE.permissionId)
        } returns PackageManager.PERMISSION_GRANTED
        every {
            ContextCompat.checkSelfPermission(context, Permission.WRITE_EXTERNAL_STORAGE.permissionId)
        } returns PackageManager.PERMISSION_DENIED

        val result = newModule(backgroundScope).awaitCurrent()

        result.missingPermission shouldContainExactly setOf(Permission.WRITE_EXTERNAL_STORAGE)
        result.paths.single().hasAccess shouldBe false
    }

    @Test
    fun `Android TV below API 33 uses READ and WRITE storage permissions`() = runTest {
        every { BuildWrap.VERSION.SDK_INT } returns 32
        every { deviceDetective.getROMType() } returns RomType.ANDROID_TV
        every { storageManager2.storageVolumes } returns listOf(
            volume(
                directory = File("/storage/emulated/0"),
                isPrimary = true,
                isEmulated = true,
            ),
        )
        every {
            ContextCompat.checkSelfPermission(context, Permission.READ_EXTERNAL_STORAGE.permissionId)
        } returns PackageManager.PERMISSION_GRANTED
        every {
            ContextCompat.checkSelfPermission(context, Permission.WRITE_EXTERNAL_STORAGE.permissionId)
        } returns PackageManager.PERMISSION_DENIED

        val result = newModule(backgroundScope).awaitCurrent()

        result.missingPermission shouldContainExactly setOf(Permission.WRITE_EXTERNAL_STORAGE)
        result.paths.single().hasAccess shouldBe false
    }

    @Test
    fun `Android TV API 33 plus uses MANAGE_EXTERNAL_STORAGE`() = runTest {
        every { BuildWrap.VERSION.SDK_INT } returns 33
        every { deviceDetective.getROMType() } returns RomType.ANDROID_TV
        every { storageManager2.storageVolumes } returns listOf(
            volume(
                directory = File("/storage/emulated/0"),
                isPrimary = true,
                isEmulated = true,
            ),
        )

        val result = newModule(backgroundScope).awaitCurrent()

        result.missingPermission shouldContainExactly setOf(Permission.MANAGE_EXTERNAL_STORAGE)
        result.paths.single().hasAccess shouldBe false
    }

    @Test
    fun `storage rows filter null directories and map labels from volume type`() = runTest {
        every { BuildWrap.VERSION.SDK_INT } returns 30
        every { Environment.isExternalStorageManager() } returns true
        every { storageManager2.storageVolumes } returns listOf(
            volume(
                directory = File("/storage/emulated/0"),
                isPrimary = true,
                isEmulated = true,
            ),
            volume(
                directory = File("/storage/ABCD-EF12"),
                isRemovable = true,
            ),
            volume(directory = null),
        )

        val result = newModule(backgroundScope).awaitCurrent()

        result.missingPermission shouldBe emptySet()
        result.paths shouldHaveSize 2
        result.paths.map { it.label.get(context) } shouldContainExactly listOf(
            context.getString(R.string.data_area_public_storage_label),
            context.getString(R.string.data_area_sdcard_label),
        )
        result.paths.map { it.hasAccess } shouldContainExactly listOf(true, true)
    }

    private fun newModule(appScope: CoroutineScope) = StorageSetupModule(
        appScope = appScope,
        context = context,
        storageManager2 = storageManager2,
        dataAreaManager = dataAreaManager,
        deviceDetective = deviceDetective,
    )

    private suspend fun StorageSetupModule.awaitCurrent(): StorageSetupModule.Result =
        state.filterIsInstance<StorageSetupModule.Result>().first()

    private fun volume(
        directory: File?,
        isPrimary: Boolean = false,
        isEmulated: Boolean = false,
        isRemovable: Boolean = false,
    ): StorageVolumeX = mockk {
        every { this@mockk.directory } returns directory
        every { this@mockk.isPrimary } returns isPrimary
        every { this@mockk.isEmulated } returns isEmulated
        every { this@mockk.isRemovable } returns isRemovable
    }
}
