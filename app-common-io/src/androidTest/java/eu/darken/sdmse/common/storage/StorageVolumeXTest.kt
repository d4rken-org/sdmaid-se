package eu.darken.sdmse.common.storage

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Characterizes [StorageManager2.storageVolumes] and [StorageVolumeX] against the hidden-API surface.
 * Enforcement is keyed on the caller's targetSdk, which `testOptions { targetSdk }` pins to the app's.
 */
@RunWith(AndroidJUnit4::class)
class StorageVolumeXTest {

    @get:Rule
    val logCapture = LogCaptureRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
    private val storageManager2 = StorageManager2(context)

    /** Descriptions are localized, so comparing them only means something at a fixed locale. */
    private val localizedContext: Context = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply { setLocale(Locale.US) }
    )

    private val volumes: List<Pair<StorageVolume, StorageVolumeX>>
        get() = storageManager.storageVolumes.map { it to StorageVolumeX(it) }

    private val primary: Pair<StorageVolume, StorageVolumeX>
        get() = volumes.single { it.second.isPrimary }

    private val sdCard: Pair<StorageVolume, StorageVolumeX>
        get() = volumes.single { !it.second.isEmulated }

    @Before
    fun setup() = logDeviceIdentity()

    @Test
    fun fixtureHasPrimaryAndSdCard() {
        val all = volumes.map { it.second }
        withClue(clue("volumes=$all")) {
            all.count { it.isPrimary && it.isEmulated } shouldBe 1
            all.count { !it.isEmulated } shouldBe 1
            all.all { it.isMounted } shouldBe true
            sdCard.second.isRemovable shouldBe true
            sdCard.second.uuid.shouldNotBeNull()
            primary.second.uuid shouldBe null
        }
    }

    /**
     * `getPath()` carries `@UnsupportedAppUsage(maxTargetSdk = Q)` *and* `@TestApi`; the test-API flag is what
     * keeps it reachable at targetSdk 36, while `getPathFile()` (same max-target, no test-API flag) is blocked.
     */
    @Test
    fun pathIsServedByReflection() {
        volumes.forEach { (raw, wrapped) ->
            val probe = raw.probe("getPath")
            withClue(clue("getPath probe=$probe wrapper=${wrapped.path}")) {
                probe.shouldBeInstanceOf<ProbeResult.Returned>()
                wrapped.path shouldBe probe.value
                // The `directory?.path` fallback in StorageVolumeX.path is dead code: methodGetPath swallows a
                // failed lookup to null, so the safe call yields null instead of throwing into the catch block.
                // It happens to be invisible here only because getPath() itself stays reachable.
                wrapped.path shouldBe wrapped.directory?.path
            }
        }
    }

    @Test
    fun pathFileReflectionFollowsEnforcement() {
        volumes.forEach { (raw, _) ->
            val probe = raw.probe("getPathFile")
            withClue(clue("getPathFile probe=$probe")) {
                when {
                    Build.VERSION.SDK_INT >= 29 -> probe.reachable shouldBe false
                    Build.VERSION.SDK_INT >= 26 -> probe.shouldBeInstanceOf<ProbeResult.Returned>()
                    else -> unpinnedSdk("StorageVolume.getPathFile")
                }
            }
        }
    }

    /**
     * `directory` reads the public `getDirectory()` from API 30 and falls back to the `getPathFile()` reflection
     * below it. API 29 sits between the two and was never run: there the reflection is already blocked while the
     * public getter does not exist yet, so `directory` is expected to be null there.
     */
    @Test
    fun directoryIsAvailable() {
        volumes.forEach { (_, wrapped) ->
            withClue(clue("directory=${wrapped.directory}")) {
                when {
                    Build.VERSION.SDK_INT >= 30 -> wrapped.directory.shouldNotBeNull()
                    Build.VERSION.SDK_INT <= 28 -> wrapped.directory.shouldNotBeNull()
                    else -> unpinnedSdk("StorageVolumeX.directory")
                }
            }
        }
    }

    /** Below 29 the member does not exist yet, from 29 it is denylisted - both end in the wrapper's own fallback. */
    @Test
    fun dumpFallsBackToToString() {
        volumes.forEach { (raw, wrapped) ->
            val probe = raw.probe("dump")
            withClue(clue("dump probe=$probe")) {
                probe.reachable shouldBe false
                wrapped.dump() shouldBe wrapped.toString()
            }
        }
        withClue(clue("captured=${logCapture.captured}")) {
            logCapture.captured.any { it.endsWith("dump() unavailable.") } shouldBe true
        }
    }

    @Test
    fun userLabelIsReachable() {
        volumes.forEach { (raw, wrapped) ->
            val probe = raw.probe("getUserLabel")
            withClue(clue("getUserLabel probe=$probe wrapper=${wrapped.userLabel}")) {
                probe.shouldBeInstanceOf<ProbeResult.Returned>()
                wrapped.userLabel.shouldNotBeNull() shouldBe probe.value
            }
        }
    }

    /** Public from API 33, `maxTargetSdk = P` on 29..32, unenforced below - only the outer ranges were observed. */
    @Test
    fun ownerIsReachable() {
        volumes.forEach { (raw, wrapped) ->
            val probe = raw.probe("getOwner")
            withClue(clue("getOwner probe=$probe wrapper=${wrapped.owner}")) {
                when {
                    Build.VERSION.SDK_INT >= 33 -> probe.shouldBeInstanceOf<ProbeResult.Returned>()
                    Build.VERSION.SDK_INT <= 28 -> probe.shouldBeInstanceOf<ProbeResult.Returned>()
                    else -> unpinnedSdk("StorageVolume.getOwner")
                }
                wrapped.owner.shouldNotBeNull()
            }
        }
    }

    /** `getDescription(Context)` is public API; the assertion is that both wrapper branches agree with it. */
    @Test
    fun descriptionMatchesThePlatform() {
        volumes.forEach { (raw, wrapped) ->
            val probe = raw.probe("getDescription", listOf(Context::class.java), listOf(localizedContext))
            withClue(clue("getDescription probe=$probe")) {
                probe.shouldBeInstanceOf<ProbeResult.Returned>()
                wrapped.getDescription(localizedContext).shouldNotBeNull() shouldBe probe.value
            }
        }
    }

    @Test
    fun primaryUrisAddressTheEmulatedRoot() {
        val wrapped = primary.second
        withClue(clue("rootUri=${wrapped.rootUri}")) {
            wrapped.rootUri.scheme shouldBe "content"
            wrapped.rootUri.authority shouldBe EXTERNAL_STORAGE_AUTHORITY
            wrapped.rootUri.pathSegments shouldContainExactly listOf("root", "primary")
            wrapped.documentUri.authority shouldBe EXTERNAL_STORAGE_AUTHORITY
            wrapped.documentUri.pathSegments shouldContainExactly listOf("document", "primary")
            wrapped.treeUri.authority shouldBe EXTERNAL_STORAGE_AUTHORITY
            wrapped.treeUri.pathSegments shouldContainExactly listOf("tree", "primary")
        }
    }

    @Test
    fun sdCardUrisAddressTheVolumeUuid() {
        val wrapped = sdCard.second
        val uuid = wrapped.uuid.shouldNotBeNull()
        withClue(clue("rootUri=${wrapped.rootUri}")) {
            wrapped.rootUri.authority shouldBe EXTERNAL_STORAGE_AUTHORITY
            wrapped.rootUri.pathSegments shouldContainExactly listOf("root", uuid)
            wrapped.documentUri.pathSegments shouldContainExactly listOf("document", uuid)
            wrapped.treeUri.pathSegments shouldContainExactly listOf("tree", uuid)
        }
    }

    @Suppress("DEPRECATION")
    @Test
    fun getStorageVolumeResolvesPrimary() {
        val resolved = storageManager2.getStorageVolume(Environment.getExternalStorageDirectory())
        withClue(clue("resolved=$resolved")) {
            resolved.shouldNotBeNull().isPrimary shouldBe true
            resolved.path shouldBe primary.second.path
        }
    }

    companion object {
        private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
    }
}
