package eu.darken.sdmse.analyzer.core.storage

import android.content.pm.ApplicationInfo
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.storage.VolumeInfoX
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.common.user.UserProfile2
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class StorageScannerTest : BaseTest() {

    private val currentUser = UserHandle2(handleId = 0)

    private fun volume(
        mountUserId: Int?,
        isMounted: Boolean = true,
        isEmulated: Boolean = true,
        isPrimary: Boolean? = true,
    ) = mockk<VolumeInfoX>().apply {
        every { this@apply.mountUserId } returns mountUserId
        every { this@apply.isMounted } returns isMounted
        every { this@apply.isEmulated } returns isEmulated
        every { this@apply.isPrimary } returns isPrimary
    }

    private fun pkg(
        name: String,
        userHandle: UserHandle2 = currentUser,
        appInfo: ApplicationInfo? = ApplicationInfo(),
    ) = mockk<Installed>(relaxed = true).apply {
        every { packageName } returns name
        every { this@apply.userHandle } returns userHandle
        every { applicationInfo } returns appInfo
    }

    @Test
    fun `other user handles come from the emulated volumes`() {
        StorageScanner.otherUserHandles(
            storageType = DeviceStorage.Type.PRIMARY,
            volumes = listOf(volume(mountUserId = 0), volume(mountUserId = 10), volume(mountUserId = 11)),
            currentUser = currentUser,
        ) shouldBe setOf(UserHandle2(10), UserHandle2(11))
    }

    @Test
    fun `the private volume's negative sentinel is not a user`() {
        // The private volume reports mountUserId=-10000, taking it at face value invents a user.
        StorageScanner.otherUserHandles(
            storageType = DeviceStorage.Type.PRIMARY,
            volumes = listOf(volume(mountUserId = -10000), volume(mountUserId = -1)),
            currentUser = currentUser,
        ).shouldBeEmpty()
    }

    @Test
    fun `non-emulated and non-primary volumes are ignored`() {
        StorageScanner.otherUserHandles(
            storageType = DeviceStorage.Type.PRIMARY,
            volumes = listOf(
                volume(mountUserId = 11, isEmulated = false),
                volume(mountUserId = 12, isPrimary = false),
                volume(mountUserId = 13, isPrimary = null),
            ),
            currentUser = currentUser,
        ).shouldBeEmpty()
    }

    @Test
    fun `an unmounted volume still counts, a stopped user still occupies storage`() {
        StorageScanner.otherUserHandles(
            storageType = DeviceStorage.Type.PRIMARY,
            volumes = listOf(volume(mountUserId = 10, isMounted = false)),
            currentUser = currentUser,
        ) shouldBe setOf(UserHandle2(10))
    }

    @Test
    fun `an unavailable volume list yields no users`() {
        StorageScanner.otherUserHandles(
            storageType = DeviceStorage.Type.PRIMARY,
            volumes = null,
            currentUser = currentUser,
        ).shouldBeEmpty()
    }

    @Test
    fun `secondary and portable storage have no other-user model`() {
        // /data/media/<id> only exists for primary storage.
        val volumes = listOf(volume(mountUserId = 10))
        StorageScanner.otherUserHandles(DeviceStorage.Type.SECONDARY, volumes, currentUser).shouldBeEmpty()
        StorageScanner.otherUserHandles(DeviceStorage.Type.PORTABLE, volumes, currentUser).shouldBeEmpty()
    }

    @Test
    fun `a user known only to the volume list is included`() {
        StorageScanner.mergeOtherUsers(
            storageType = DeviceStorage.Type.PRIMARY,
            volumes = listOf(volume(mountUserId = 10)),
            named = emptySet(),
            currentUser = currentUser,
        ) shouldBe setOf(UserProfile2(handle = UserHandle2(10)))
    }

    @Test
    fun `a user known only to the user list is included`() {
        // API 27/28 expose a single `emulated` volume with mountUserId=-1, gating on the volume
        // list alone hides every secondary user there.
        StorageScanner.mergeOtherUsers(
            storageType = DeviceStorage.Type.PRIMARY,
            volumes = listOf(volume(mountUserId = -1)),
            named = setOf(UserProfile2(handle = UserHandle2(10), label = "Second user")),
            currentUser = currentUser,
        ) shouldBe setOf(UserProfile2(handle = UserHandle2(10), label = "Second user"))
    }

    @Test
    fun `both sources are unioned and names win over bare handles`() {
        StorageScanner.mergeOtherUsers(
            storageType = DeviceStorage.Type.PRIMARY,
            volumes = listOf(volume(mountUserId = 10), volume(mountUserId = 11)),
            named = setOf(UserProfile2(handle = UserHandle2(10), label = "Second user")),
            currentUser = currentUser,
        ) shouldBe setOf(
            UserProfile2(handle = UserHandle2(10), label = "Second user"),
            UserProfile2(handle = UserHandle2(11)),
        )
    }

    @Test
    fun `the current user is not an other user, from either source`() {
        StorageScanner.mergeOtherUsers(
            storageType = DeviceStorage.Type.PRIMARY,
            volumes = listOf(volume(mountUserId = 0)),
            named = setOf(UserProfile2(handle = currentUser, label = "Owner")),
            currentUser = currentUser,
        ).shouldBeEmpty()
    }

    @Test
    fun `negative handles from either source are not users`() {
        // -1 is the "unknown" sentinel of the pre-API29 emulated volume, -10000 the private volume.
        StorageScanner.mergeOtherUsers(
            storageType = DeviceStorage.Type.PRIMARY,
            volumes = listOf(volume(mountUserId = -10000)),
            named = setOf(UserProfile2(handle = UserHandle2(-1), label = "System")),
            currentUser = currentUser,
        ).shouldBeEmpty()
    }

    @Test
    fun `secondary and portable storage have no other users at all`() {
        val volumes = listOf(volume(mountUserId = 10))
        val named = setOf(UserProfile2(handle = UserHandle2(11)))
        StorageScanner.mergeOtherUsers(DeviceStorage.Type.SECONDARY, volumes, named, currentUser).shouldBeEmpty()
        StorageScanner.mergeOtherUsers(DeviceStorage.Type.PORTABLE, volumes, named, currentUser).shouldBeEmpty()
    }

    @Test
    fun `app scan targets are limited to the current user`() {
        val targets = StorageScanner.scanTargets(
            pkgs = listOf(
                pkg("com.example.mine"),
                pkg("com.example.theirs", userHandle = UserHandle2(10)),
                pkg("android"),
                pkg("com.example.noinfo", appInfo = null),
            ),
            currentUser = currentUser,
        )

        targets.map { it.packageName } shouldBe listOf("com.example.mine")
    }

    @Test
    fun `the residual is what apps, media and other users don't account for`() {
        StorageScanner.computeResidual(
            spaceUsed = 1_000L,
            apps = 300L,
            media = 200L,
            otherUsers = 100L,
        ) shouldBe 400L
    }

    @Test
    fun `over-accounted input clamps the residual to zero`() {
        // The degraded scan du-sizes media folders, which can overcount past the used space.
        StorageScanner.computeResidual(
            spaceUsed = 1_000L,
            apps = 800L,
            media = 400L,
            otherUsers = 100L,
        ) shouldBe 0L
    }

    @Test
    fun `without other users the residual is unchanged`() {
        StorageScanner.computeResidual(
            spaceUsed = 1_000L,
            apps = 300L,
            media = 200L,
            otherUsers = 0L,
        ) shouldBe 500L
    }
}
