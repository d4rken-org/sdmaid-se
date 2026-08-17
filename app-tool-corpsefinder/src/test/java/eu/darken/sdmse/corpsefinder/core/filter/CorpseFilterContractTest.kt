package eu.darken.sdmse.corpsefinder.core.filter

import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.corpsefinder.R
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.coroutine.runTest2
import java.io.IOException

/**
 * Pins the behaviour the per-filter tests don't look at: progress labels and counts, which
 * capability methods a filter calls, how often it reads the risk settings and what a failure
 * mid-scan leaves behind.
 *
 * These are all things that are invisible in the corpse results, so they need explicit coverage.
 */
class CorpseFilterContractTest : CorpseFilterTest() {

    @MockK lateinit var context: Context
    @MockK lateinit var packageManager: PackageManager

    /** Resolves [CaString]s to a marker for the resource id they carry. */
    private lateinit var resContext: Context

    @BeforeEach
    override fun setup() {
        super.setup()
        every { context.packageManager } returns packageManager
        every { packageManager.getSharedLibraries(0) } returns mutableListOf()

        resContext = mockk<Context>().apply {
            every { getString(any<Int>()) } answers { stringRes(firstArg<Int>()) }
            every { getString(any<Int>(), *anyVararg()) } answers { stringRes(firstArg<Int>()) }
        }
    }

    private fun stringRes(@StringRes id: Int): String = "res#$id"

    private fun CaString.resolved(): String = get(resContext)

    /**
     * The current progress state.
     *
     * [CorpseFilter.progress] is conflated and throttled, so collecting it drops exactly the
     * intermediate states these tests are about. [Progress.Client.updateProgress] reads the same
     * state synchronously and without side effects.
     */
    private fun CorpseFilter.currentProgress(): Progress.Data? {
        var snapshot: Progress.Data? = null
        updateProgress { snapshot = it; it }
        return snapshot
    }

    private fun appSourceFilter() = AppSourceCorpseFilter(
        areaManager = areaManager,
        gatewaySwitch = gatewaySwitch,
        fileForensics = fileForensics,
        corpseFinderSettings = corpseFinderSettings,
        exclusionManager = exclusionManager,
    )

    private fun appSourcePrivateFilter() = AppSourcePrivateCorpseFilter(
        areaManager = areaManager,
        gatewaySwitch = gatewaySwitch,
        fileForensics = fileForensics,
        corpseFinderSettings = corpseFinderSettings,
        exclusionManager = exclusionManager,
    )

    private fun appLibFilter() = AppLibCorpseFilter(
        areaManager = areaManager,
        gatewaySwitch = gatewaySwitch,
        fileForensics = fileForensics,
        corpseFinderSettings = corpseFinderSettings,
        exclusionManager = exclusionManager,
    )

    private fun appAsecFilter() = AppAsecFileCorpseFilter(
        areaManager = areaManager,
        gatewaySwitch = gatewaySwitch,
        fileForensics = fileForensics,
        corpseFinderSettings = corpseFinderSettings,
        exclusionManager = exclusionManager,
    )

    private fun artProfilesFilter() = ArtProfilesCorpseFilter(
        areaManager = areaManager,
        gatewaySwitch = gatewaySwitch,
        fileForensics = fileForensics,
        corpseFinderSettings = corpseFinderSettings,
        exclusionManager = exclusionManager,
    )

    private fun privateDataFilter() = PrivateDataCorpseFilter(
        areaManager = areaManager,
        gatewaySwitch = gatewaySwitch,
        fileForensics = fileForensics,
        corpseFinderSettings = corpseFinderSettings,
        exclusionManager = exclusionManager,
    )

    private fun publicDataFilter() = PublicDataCorpseFilter(
        areaManager = areaManager,
        gatewaySwitch = gatewaySwitch,
        fileForensics = fileForensics,
        corpseFinderSettings = corpseFinderSettings,
        exclusionManager = exclusionManager,
    )

    private fun publicObbFilter() = PublicObbCorpseFilter(
        areaManager = areaManager,
        gatewaySwitch = gatewaySwitch,
        fileForensics = fileForensics,
        corpseFinderSettings = corpseFinderSettings,
        exclusionManager = exclusionManager,
    )

    private fun publicMediaFilter() = PublicMediaCorpseFilter(
        areaManager = areaManager,
        gatewaySwitch = gatewaySwitch,
        fileForensics = fileForensics,
        corpseFinderSettings = corpseFinderSettings,
        exclusionManager = exclusionManager,
    )

    private fun dalvikFilter() = DalvikCorpseFilter(
        context = context,
        areaManager = areaManager,
        gatewaySwitch = gatewaySwitch,
        fileForensics = fileForensics,
        corpseFinderSettings = corpseFinderSettings,
        exclusionManager = exclusionManager,
    )

    // ─────────────────────────── progress labels ───────────────────────────

    @Test fun `art profiles shows the dalvik label when idle and the art label while scanning`() = runTest2 {
        val filter = artProfilesFilter()
        candidate(artProfile1, "com.orphan.pkg")

        filter.currentProgress()!!.primary.resolved() shouldBe stringRes(R.string.corpsefinder_filter_dalvik_label)

        val whileScanning = mutableListOf<String>()
        onListFiles = { whileScanning += filter.currentProgress()!!.primary.resolved() }

        filter.scan()

        whileScanning.distinct() shouldBe listOf(stringRes(R.string.corpsefinder_filter_artprofiles_label))
        filter.currentProgress()!!.primary.resolved() shouldBe stringRes(R.string.corpsefinder_filter_dalvik_label)
    }

    @Test fun `public data uses the same label when idle and while scanning`() = runTest2 {
        val filter = publicDataFilter()
        candidate(publicData1, "com.orphan.pkg", preset = Preset.StaleOwner())
        val expected = stringRes(R.string.corpsefinder_filter_publicdata_label)

        filter.currentProgress()!!.primary.resolved() shouldBe expected

        val whileScanning = mutableListOf<String>()
        onListFiles = { whileScanning += filter.currentProgress()!!.primary.resolved() }

        filter.scan()

        whileScanning.distinct() shouldBe listOf(expected)
        filter.currentProgress()!!.primary.resolved() shouldBe expected
    }

    @Test fun `app source uses the same label when idle and while scanning`() = runTest2 {
        val filter = appSourceFilter()
        candidate(appSource1, "com.orphan.pkg")
        val expected = stringRes(R.string.corpsefinder_filter_appsource_label)

        filter.currentProgress()!!.primary.resolved() shouldBe expected

        val whileScanning = mutableListOf<String>()
        onListFiles = { whileScanning += filter.currentProgress()!!.primary.resolved() }

        filter.scan()

        whileScanning.distinct() shouldBe listOf(expected)
        filter.currentProgress()!!.primary.resolved() shouldBe expected
    }

    // ─────────────────────────── indeterminate while listing ───────────────────────────

    /** Progress count at the moment each area of [areaType] gets listed. */
    private suspend fun countsWhileListing(filter: CorpseFilter): List<Progress.Count> {
        val counts = mutableListOf<Progress.Count>()
        onListFiles = { counts += filter.currentProgress()!!.count }
        filter.scan()
        return counts
    }

    @Test fun `art profiles goes indeterminate before listing each area`() = runTest2 {
        candidate(artProfile1, "com.orphan.one")
        candidate(artProfile2, "com.orphan.two")

        countsWhileListing(artProfilesFilter()) shouldBe listOf(
            Progress.Count.Indeterminate(),
            Progress.Count.Indeterminate(),
        )
    }

    @Test fun `private data goes indeterminate before listing each area`() = runTest2 {
        candidate(privateData1, "com.orphan.one", preset = Preset.StaleOwner())
        candidate(privateData2, "com.orphan.two", preset = Preset.StaleOwner())

        countsWhileListing(privateDataFilter()) shouldBe listOf(
            Progress.Count.Indeterminate(),
            Progress.Count.Indeterminate(),
        )
    }

    @Test fun `public data keeps the previous area count while listing the next one`() = runTest2 {
        candidate(publicData1, "com.orphan.one", preset = Preset.StaleOwner())
        candidate(publicData2, "com.orphan.two", preset = Preset.StaleOwner())

        // No indeterminate reset here: the second area is listed while the first area's percentage
        // is still on display.
        countsWhileListing(publicDataFilter()) shouldBe listOf(
            Progress.Count.Indeterminate(),
            Progress.Count.Percent(1, 1),
        )
    }

    // ─────────────────────────── progress denominator ───────────────────────────

    @Test fun `name excluded candidates count towards the total but never advance progress`() = runTest2 {
        val excluded = candidate(publicData1, ".nomedia", preset = Preset.StaleOwner())
        candidate(publicData1, "com.orphan.pkg", preset = Preset.StaleOwner())
        val filter = publicDataFilter()

        val counts = mutableListOf<Progress.Count>()
        onFindOwners = { counts += filter.currentProgress()!!.count }

        filter.scan()

        // Two candidates were counted, but only the one that survived the name check advanced.
        counts shouldBe listOf(Progress.Count.Percent(1, 2))
        coVerify(exactly = 0) { fileForensics.findOwners(excluded.path) }
    }

    // ─────────────────────────── gateway and capability usage ───────────────────────────

    @Test fun `public media never asks for the local gateway`() = runTest2 {
        candidate(publicMedia1, "com.orphan.pkg", preset = Preset.StaleOwner())

        publicMediaFilter().scan()

        coVerify(exactly = 0) { gatewaySwitch.getGateway(any()) }
    }

    @Test fun `public data takes the gateway but no capability below API 33`() = runTest2 {
        fakeSdk(32)
        candidate(publicData1, "com.orphan.pkg", preset = Preset.StaleOwner())

        publicDataFilter().scan()

        coVerify(exactly = 1) { gatewaySwitch.getGateway(APath.PathType.LOCAL) }
        coVerify(exactly = 0) { localGateway.hasRoot() }
        coVerify(exactly = 0) { localGateway.hasAdb() }
    }

    @Test fun `public obb takes the gateway but no capability below API 33`() = runTest2 {
        fakeSdk(32)
        candidate(publicObb1, "com.orphan.pkg")

        publicObbFilter().scan()

        coVerify(exactly = 1) { gatewaySwitch.getGateway(APath.PathType.LOCAL) }
        coVerify(exactly = 0) { localGateway.hasRoot() }
        coVerify(exactly = 0) { localGateway.hasAdb() }
    }

    @Test fun `public data skips the adb check when it has root on API 33`() = runTest2 {
        fakeSdk(33)
        hasRoot(true)
        candidate(publicData1, "com.orphan.pkg", preset = Preset.StaleOwner())

        publicDataFilter().scan()

        coVerify(exactly = 1) { localGateway.hasRoot() }
        coVerify(exactly = 0) { localGateway.hasAdb() }
    }

    @Test fun `public data falls back to the adb check without root on API 33`() = runTest2 {
        fakeSdk(33)
        hasRoot(false)
        hasAdb(true)
        candidate(publicData1, "com.orphan.pkg", preset = Preset.StaleOwner())

        publicDataFilter().scan()

        coVerify(exactly = 1) { localGateway.hasRoot() }
        coVerify(exactly = 1) { localGateway.hasAdb() }
    }

    @Test fun `public obb never asks for adb with root on API 33`() = runTest2 {
        fakeSdk(33)
        hasRoot(true)
        candidate(publicObb1, "com.orphan.pkg")

        publicObbFilter().scan()

        coVerify(exactly = 1) { localGateway.hasRoot() }
        coVerify(exactly = 0) { localGateway.hasAdb() }
    }

    @Test fun `public obb never asks for adb without root on API 33`() = runTest2 {
        fakeSdk(33)
        hasRoot(false)
        hasAdb(true)
        candidate(publicObb1, "com.orphan.pkg")

        publicObbFilter().scan()

        coVerify(exactly = 1) { localGateway.hasRoot() }
        coVerify(exactly = 0) { localGateway.hasAdb() }
    }

    /**
     * The root check comes first, so a rootless device never even reaches the API cutoff, and adb
     * is not an option for these filters.
     */
    private suspend fun assertRootIsCheckedBeforeTheApiBail(bailApi: Int, filter: CorpseFilter) {
        fakeSdk(bailApi)
        hasRoot(false)
        hasAdb(true)

        filter.scan() shouldBe emptySet()

        coVerify(exactly = 1) { gatewaySwitch.getGateway(APath.PathType.LOCAL) }
        coVerify(exactly = 1) { localGateway.hasRoot() }
        coVerify(exactly = 0) { localGateway.hasAdb() }
        coVerify(exactly = 0) { gatewaySwitch.listFiles(any()) }
    }

    @Test fun `app source checks root before its API bail`() = runTest2 {
        assertRootIsCheckedBeforeTheApiBail(37, appSourceFilter())
    }

    @Test fun `app source private checks root before its API bail`() = runTest2 {
        assertRootIsCheckedBeforeTheApiBail(35, appSourcePrivateFilter())
    }

    @Test fun `app lib checks root before its API bail`() = runTest2 {
        assertRootIsCheckedBeforeTheApiBail(35, appLibFilter())
    }

    @Test fun `app asec checks root before its API bail`() = runTest2 {
        assertRootIsCheckedBeforeTheApiBail(35, appAsecFilter())
    }

    @Test fun `art profiles checks root before its API bail`() = runTest2 {
        assertRootIsCheckedBeforeTheApiBail(37, artProfilesFilter())
    }

    @Test fun `private data checks root before its API bail`() = runTest2 {
        assertRootIsCheckedBeforeTheApiBail(37, privateDataFilter())
    }

    // ─────────────────────────── risk setting reads ───────────────────────────

    @Test fun `the standard skeleton reads the risk settings once per area`() = runTest2 {
        candidate(publicData1, "com.orphan.one", preset = Preset.StaleOwner())
        candidate(publicData2, "com.orphan.two", preset = Preset.StaleOwner())

        publicDataFilter().scan()

        verify(exactly = 2) { keeperSetting.flow }
        verify(exactly = 2) { commonSetting.flow }
    }

    @Test fun `dalvik reads the risk settings once for both of its areas`() = runTest2 {
        candidate(dalvikProfile1, "com.orphan.profile")
        candidate(dalvikDex1, "com.orphan.dex")

        dalvikFilter().scan()

        verify(exactly = 1) { keeperSetting.flow }
        verify(exactly = 1) { commonSetting.flow }
    }

    // ─────────────────────────── failures mid-scan ───────────────────────────

    @Test fun `a failing listing propagates and still resets progress`() = runTest2 {
        candidate(publicData1, "com.orphan.pkg", preset = Preset.StaleOwner())
        val filter = publicDataFilter()
        val idle = filter.currentProgress()
        coEvery { gatewaySwitch.listFiles(any()) } throws IOException("Listing failed")

        shouldThrow<IOException> { filter.scan() }

        filter.currentProgress() shouldBe idle
    }

    @Test fun `failing forensics propagate and still reset progress`() = runTest2 {
        val target = candidate(publicData1, "com.orphan.pkg", preset = Preset.StaleOwner())
        val filter = publicDataFilter()
        val idle = filter.currentProgress()
        coEvery { fileForensics.findOwners(target.path) } throws IOException("Forensics failed")

        shouldThrow<IOException> { filter.scan() }

        filter.currentProgress() shouldBe idle
    }

    @Test fun `a failing lookup propagates and still resets progress`() = runTest2 {
        val target = candidate(publicData1, "com.orphan.pkg", preset = Preset.StaleOwner())
        val filter = publicDataFilter()
        val idle = filter.currentProgress()
        coEvery { gatewaySwitch.lookup(target.path) } throws IOException("Lookup failed")

        shouldThrow<IOException> { filter.scan() }

        filter.currentProgress() shouldBe idle
    }
}
