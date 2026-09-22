package eu.darken.sdmse.common.storage

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.kotest.assertions.withClue
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Characterizes [StorageManager2.volumes], [VolumeInfoX] and [DiskInfoX], which have no public API equivalent at
 * all - every value here comes out of reflection, and every accessor answers null when it breaks.
 */
@RunWith(AndroidJUnit4::class)
class VolumeInfoXTest {

    @get:Rule
    val logCapture = LogCaptureRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
    private val storageManager2 = StorageManager2(context)

    private val rawVolumes: List<Any>
        get() {
            val probe = storageManager.probe("getVolumes")
            withClue(clue("getVolumes probe=$probe")) { probe.shouldBeInstanceOf<ProbeResult.Returned>() }
            return ((probe as ProbeResult.Returned).value as List<*>).filterNotNull()
        }

    private val volumes: List<VolumeInfoX>
        get() = storageManager2.volumes.shouldNotBeNull()

    private val emulated: VolumeInfoX
        get() = volumes.single { it.isEmulated }

    private val diskBacked: List<VolumeInfoX>
        get() = volumes.filter { it.disk != null }

    @Before
    fun setup() = logDeviceIdentity()

    @Test
    fun getVolumesIsReachable() {
        withClue(clue("raw=${rawVolumes.size} wrapped=${storageManager2.volumes?.size}")) {
            rawVolumes.size shouldBeGreaterThanOrEqual 3
            volumes.size shouldBe rawVolumes.size
        }
    }

    /** "Usable" for every consumer of this list means id, type, state and path all survived reflection. */
    @Test
    fun everyVolumeIsUsable() {
        rawVolumes.map { it to VolumeInfoX(it) }.forEach { (raw, wrapped) ->
            withClue(
                clue(
                    "volume id=${wrapped.id} type=${wrapped.type} state=${wrapped.state} path=${wrapped.path} " +
                        "mountUserId=${wrapped.mountUserId} isPrimary=${wrapped.isPrimary}"
                )
            ) {
                wrapped.id.shouldNotBeNull() shouldBe (raw.probe("getId") as ProbeResult.Returned).value
                wrapped.type.shouldNotBeNull() shouldBe (raw.probe("getType") as ProbeResult.Returned).value
                wrapped.state.shouldNotBeNull() shouldBe (raw.probe("getState") as ProbeResult.Returned).value
                wrapped.path.shouldNotBeNull() shouldBe (raw.probe("getPath") as ProbeResult.Returned).value
                wrapped.isPrimary.shouldNotBeNull()
                wrapped.mountUserId.shouldNotBeNull()
                wrapped.isMounted shouldBe true
            }
        }
    }

    @Test
    fun privateAndEmulatedVolumesArePresent() {
        withClue(clue("volumes=$volumes")) {
            val privateVolume = volumes.single { it.isPrivate }
            privateVolume.id shouldBe "private"
            privateVolume.path shouldBe File("/data")
            privateVolume.disk shouldBe null
            privateVolume.fsUuid shouldBe null
            privateVolume.isRemovable shouldBe false

            emulated.isPrimary shouldBe true
            emulated.path shouldBe File("/storage/emulated")
            emulated.fsUuid shouldBe null
            emulated.disk shouldBe null
        }
    }

    /**
     * The emulated volume is mounted per user from some level above 28, which renames it from "emulated" to
     * "emulated;<userId>". [VolumeInfoX.isRemovable] compares against the old id only, so internal shared storage
     * reports itself as removable wherever the per-user id is used. Pinned as observed, not as intended.
     */
    @Test
    fun emulatedVolumeIdentityIsLevelDependent() {
        withClue(clue("emulated id=${emulated.id} mountUserId=${emulated.mountUserId} isRemovable=${emulated.isRemovable}")) {
            when {
                Build.VERSION.SDK_INT >= 36 -> {
                    emulated.id shouldBe "emulated;0"
                    emulated.mountUserId shouldBe 0
                    emulated.isRemovable shouldBe true
                }

                Build.VERSION.SDK_INT <= 28 -> {
                    emulated.id shouldBe "emulated"
                    emulated.mountUserId shouldBe -1
                    emulated.isRemovable shouldBe false
                }

                else -> unpinnedSdk("VolumeInfoX emulated volume identity")
            }
        }
    }

    @Test
    fun emulatedVolumeResolvesPerUserPaths() {
        withClue(clue("emulated=$emulated")) {
            emulated.getPathForUser(0) shouldBe File(emulated.path.shouldNotBeNull(), "0")
            volumes.single { it.isPrivate }.getPathForUser(0) shouldBe null
        }
    }

    /** The AVDs carry an SD card so that a public volume with a backing DiskInfo exists at all. */
    @Test
    fun aVolumeHasABackingDisk() {
        withClue(clue("diskBacked=$diskBacked")) {
            diskBacked.size shouldBeGreaterThanOrEqual 1
            diskBacked.forEach {
                it.isPrivate shouldBe false
                it.isEmulated shouldBe false
                it.isRemovable shouldBe true
                it.fsUuid.shouldNotBeNull()
            }
        }
    }

    /** The fixture is an SD card, so `isSd` is the true predicate. */
    @Test
    fun backingDiskIsAnSdCard() {
        val raw = rawVolumes.map { it to VolumeInfoX(it) }.first { it.second.disk != null }
        val rawDisk = (raw.first.probe("getDisk") as ProbeResult.Returned).value.shouldNotBeNull()
        val disk = raw.second.disk.shouldNotBeNull()
        withClue(
            clue(
                "disk id=${disk.id} isSd=${disk.isSd} isUsb=${disk.isUsb} isAdoptable=${disk.isAdoptable} " +
                    "isDefaultPrimary=${disk.isDefaultPrimary}"
            )
        ) {
            disk.id.shouldNotBeNull() shouldBe (rawDisk.probe("getId") as ProbeResult.Returned).value
            disk.description.shouldNotBeNull()
            disk.isSd shouldBe true
            disk.isUsb shouldBe false
            disk.isDefaultPrimary shouldBe false
            // Follows the AVD's disk flags rather than the platform, so only availability is pinned.
            disk.isAdoptable.shouldNotBeNull()
        }
    }

    @Test
    fun environmentForStateIsReachable() {
        withClue(clue("getEnvironmentForState")) {
            VolumeInfoX.getEnvironmentForState(VolumeInfoX.STATE_MOUNTED) shouldBe Environment.MEDIA_MOUNTED
        }
    }
}
