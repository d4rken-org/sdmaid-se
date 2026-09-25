package eu.darken.sdmse.setup.shizuku

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import eu.darken.sdmse.common.adb.AdbSettings
import eu.darken.sdmse.common.adb.shizuku.AdbAvailability
import eu.darken.sdmse.common.adb.shizuku.AdbBackend
import eu.darken.sdmse.common.adb.shizuku.AdbLink
import eu.darken.sdmse.common.adb.shizuku.AdbPermission
import eu.darken.sdmse.common.adb.shizuku.ShizukuManager
import eu.darken.sdmse.common.adb.shizuku.ShizukuServiceState
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.root.RootManager
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.flow.test

class ShizukuSetupModuleTest : BaseTest() {

    private val context: Context = mockk()
    private val packageManager: PackageManager = mockk()
    private val adbSettings: AdbSettings = mockk()
    private val shizukuManager: ShizukuManager = mockk()
    private val dataAreaManager: DataAreaManager = mockk(relaxed = true)
    private val rootManager: RootManager = mockk()

    private val useShizukuValue: DataStoreValue<Boolean?> = mockk()
    private lateinit var useShizukuFlow: MutableStateFlow<Boolean?>
    private lateinit var linkFlow: MutableStateFlow<AdbLink?>
    private lateinit var scope: CoroutineScope
    private var probeCount = 0

    private val shizukuPkg = "moe.shizuku.privileged.api".toPkgId()
    private val porterPkg = "eu.darken.porter".toPkgId()

    @BeforeEach
    fun setup() {
        probeCount = 0
        useShizukuFlow = MutableStateFlow(true)
        linkFlow = MutableStateFlow(null)
        scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())

        every { context.packageManager } returns packageManager
        every { packageManager.getLaunchIntentForPackage(any()) } returns mockk<Intent>()
        // Default: no label available, so managerLabel stays null and the card falls back to the
        // backend's own name. Tests that care about the label override this.
        every {
            packageManager.getPackageInfo(any<String>(), any<Int>())
        } throws PackageManager.NameNotFoundException()

        every { adbSettings.useShizuku } returns useShizukuValue
        every { useShizukuValue.flow } returns useShizukuFlow
        coEvery { useShizukuValue.update(any()) } answers {
            val old = useShizukuFlow.value
            val new = firstArg<(Boolean?) -> Boolean?>().invoke(old)
            useShizukuFlow.value = new
            DataStoreValue.Updated(old = old, new = new)
        }

        every { shizukuManager.adbLink } returns linkFlow
        every { shizukuManager.permissionChanges } returns emptyFlow()
        every { shizukuManager.useShizuku } returns flowOf(false)
        coEvery { shizukuManager.availability() } returns AdbAvailability.Installed(
            backend = AdbBackend.SHIZUKU,
            packageName = shizukuPkg.name,
            connected = true,
        )
        coEvery { shizukuManager.backendOf(any()) } answers {
            when (val availability = firstArg<AdbAvailability?>()) {
                is AdbAvailability.Installed -> availability.backend
                is AdbAvailability.Incompatible -> availability.backend
                AdbAvailability.NotInstalled, null -> AdbBackend.SHIZUKU
            }
        }
        coEvery { shizukuManager.referenceManagerId(any()) } returns shizukuPkg
        coEvery { shizukuManager.getManagerId(any()) } returns shizukuPkg
        coEvery { shizukuManager.managerIds() } returns setOf(shizukuPkg)
        coEvery { shizukuManager.activeManagerIds(any()) } returns setOf(shizukuPkg)
        coEvery { shizukuManager.priorityBlockedManagerId(any()) } returns null
        coEvery { shizukuManager.activeBackend() } returns AdbBackend.SHIZUKU
        coEvery { shizukuManager.isGranted() } returns true
        coEvery { shizukuManager.requestPermission() } returns AdbPermission.GRANTED
        coEvery { shizukuManager.getServiceState() } coAnswers { probeCount++; ShizukuServiceState.Available }

        every { rootManager.useRoot } returns flowOf(false)
    }

    @AfterEach
    fun teardown() {
        scope.cancel()
    }

    private fun module(
        moduleScope: CoroutineScope = scope,
        dispatchers: DispatcherProvider = TestDispatcherProvider(),
    ) = ShizukuSetupModule(
        context,
        moduleScope,
        dispatchers,
        adbSettings,
        shizukuManager,
        dataAreaManager,
        rootManager,
    )

    @Test fun `first subscription emits Loading then Result`() {
        val mod = module()

        val collector = mod.state.test(tag = "first", scope = scope)
        collector.await { values, _ -> values.any { it is ShizukuSetupModule.Result } }

        collector.latestValues.first().shouldBeInstanceOf<ShizukuSetupModule.Loading>()
        val result = collector.latestValues.last().shouldBeInstanceOf<ShizukuSetupModule.Result>()
        result.ourService shouldBe true

        runBlocking { collector.cancelAndJoin() }
    }

    @Test fun `re-subscription emits cached Result instead of Loading`() {
        val mod = module()

        val first = mod.state.test(tag = "first", scope = scope)
        first.await { values, _ -> values.any { it is ShizukuSetupModule.Result } }
        runBlocking { first.cancelAndJoin() }
        runBlocking { delay(50) } // let the share fully stop (clears the replay buffer)

        // Returning to the dashboard: the cached Result must come first so the setup card doesn't
        // flicker to Loading while the probe re-runs.
        val second = mod.state.test(tag = "second", scope = scope)
        second.await { values, _ -> values.isNotEmpty() }

        second.latestValues.first().shouldBeInstanceOf<ShizukuSetupModule.Result>()

        runBlocking { second.cancelAndJoin() }
    }

    @Test fun `re-subscription still re-runs the probe in the background`() {
        val mod = module()

        val first = mod.state.test(tag = "first", scope = scope)
        first.await { values, _ -> values.any { it is ShizukuSetupModule.Result } }
        runBlocking { first.cancelAndJoin() }
        runBlocking { delay(50) }

        val before = probeCount
        val second = mod.state.test(tag = "second", scope = scope)
        second.await { _, _ -> probeCount > before } // doesn't trust the cache blindly

        probeCount shouldBeGreaterThan before

        runBlocking { second.cancelAndJoin() }
    }

    @Test fun `setting change while unsubscribed does not replay stale cache`() {
        val mod = module()

        val first = mod.state.test(tag = "first", scope = scope)
        first.await { values, _ -> values.any { it is ShizukuSetupModule.Result } }
        runBlocking { first.cancelAndJoin() }
        runBlocking { delay(50) }

        // User turns Shizuku off while nothing observes the module.
        useShizukuFlow.value = false

        val second = mod.state.test(tag = "second", scope = scope)
        second.await { values, _ -> values.isNotEmpty() }

        // Cached Result was for useShizuku=true and must not be replayed for the new setting.
        second.latestValues.first().shouldBeInstanceOf<ShizukuSetupModule.Loading>()

        runBlocking { second.cancelAndJoin() }
    }

    @Test fun `refresh triggers a fresh probe`() {
        val mod = module()

        val collector = mod.state.test(tag = "refresh", scope = scope)
        collector.await { values, _ -> values.any { it is ShizukuSetupModule.Result } }
        val before = probeCount

        runBlocking { mod.refresh() }
        collector.await { _, _ -> probeCount > before }

        probeCount shouldBeGreaterThan before

        runBlocking { collector.cancelAndJoin() }
    }

    @Test fun `an unknown availability still settles a Result`() {
        coEvery { shizukuManager.availability() } returns null

        val collector = module().state.test(tag = "unknown", scope = scope)
        val settled = collector.await { _, latest ->
            latest is ShizukuSetupModule.Result && !latest.isChecking
        }.shouldBeInstanceOf<ShizukuSetupModule.Result>()

        settled.backend shouldBe AdbBackend.SHIZUKU
        settled.managerTooOld shouldBe false
        settled.sdMaidTooOld shouldBe false

        runBlocking { collector.cancelAndJoin() }
    }

    // --- service state -------------------------------------------------------------------------

    @Test fun `the probe announces itself before it settles`() {
        // A cold bind can take the whole ADB connect budget. Without an in-flight state the card
        // keeps offering a retry button that silently does nothing for those seconds.
        val mod = module()

        val collector = mod.state.test(tag = "checking", scope = scope)
        collector.await { values, _ -> values.any { it is ShizukuSetupModule.Result && !it.isChecking } }

        val results = collector.latestValues.filterIsInstance<ShizukuSetupModule.Result>()
        results.first().isChecking shouldBe true
        results.last().isChecking shouldBe false

        runBlocking { collector.cancelAndJoin() }
    }

    @Test fun `only Available counts as our service being up`() {
        fun resultWith(state: ShizukuServiceState) = ShizukuSetupModule.Result(
            pkg = shizukuPkg,
            useShizuku = true,
            serviceState = state,
        )

        resultWith(ShizukuServiceState.Available).ourService shouldBe true
        resultWith(ShizukuServiceState.NotChecked).ourService shouldBe false
        resultWith(ShizukuServiceState.PermissionDenied(permanently = false)).ourService shouldBe false
        resultWith(ShizukuServiceState.PermissionDenied(permanently = true)).ourService shouldBe false
        resultWith(ShizukuServiceState.Unknown).ourService shouldBe false
        resultWith(ShizukuServiceState.TimedOut).ourService shouldBe false
        resultWith(ShizukuServiceState.Failed).ourService shouldBe false
    }

    @Test fun `isComplete truth table across every service outcome`() {
        fun complete(
            useShizuku: Boolean?,
            isInstalled: Boolean = true,
            managerTooOld: Boolean = false,
            sdMaidTooOld: Boolean = false,
            state: ShizukuServiceState = ShizukuServiceState.NotChecked,
        ) = ShizukuSetupModule.Result(
            pkg = shizukuPkg,
            useShizuku = useShizuku,
            isInstalled = isInstalled,
            managerTooOld = managerTooOld,
            sdMaidTooOld = sdMaidTooOld,
            serviceState = state,
        ).isComplete

        // Opted in and working is the only complete "on" state.
        complete(true, state = ShizukuServiceState.Available) shouldBe true
        complete(true, state = ShizukuServiceState.TimedOut) shouldBe false
        complete(true, state = ShizukuServiceState.Failed) shouldBe false
        complete(true, state = ShizukuServiceState.PermissionDenied(permanently = false)) shouldBe false
        complete(true, state = ShizukuServiceState.PermissionDenied(permanently = true)) shouldBe false
        complete(true, state = ShizukuServiceState.Unknown) shouldBe false
        complete(true, state = ShizukuServiceState.NotChecked) shouldBe false

        // Wants Shizuku but it isn't installed stays incomplete, even when nothing failed.
        complete(true, isInstalled = false, state = ShizukuServiceState.Available) shouldBe false

        // An incompatible manager keeps the card up, it needs an update on one side.
        complete(true, managerTooOld = true, state = ShizukuServiceState.Unknown) shouldBe false
        complete(true, sdMaidTooOld = true, state = ShizukuServiceState.Unknown) shouldBe false

        // Opted out is settled, undecided is not.
        complete(false, state = ShizukuServiceState.TimedOut) shouldBe true
        complete(null) shouldBe false
    }

    @Test fun `a probe that throws settles as Failed instead of stranding the checking state`() {
        // Regression guard: emitting isChecking=true and THEN throwing kills the sharing coroutine
        // with that state stuck in replayingShare's replay slot. No refresh can replace it, so every
        // later subscriber inherits a permanently disabled retry button.
        coEvery { shizukuManager.getServiceState() } throws RuntimeException("link died")
        val mod = module()

        val collector = mod.state.test(tag = "throwing", scope = scope)
        collector.await { values, _ -> values.any { it is ShizukuSetupModule.Result && !it.isChecking } }

        val settled = collector.latestValues
            .filterIsInstance<ShizukuSetupModule.Result>()
            .last { !it.isChecking }
        settled.serviceState shouldBe ShizukuServiceState.Failed
        settled.isChecking shouldBe false

        runBlocking { collector.cancelAndJoin() }
    }

    // --- card package --------------------------------------------------------------------------

    private fun firstResult(mod: ShizukuSetupModule): ShizukuSetupModule.Result {
        val collector = mod.state.test(tag = "pkg", scope = scope)
        collector.await { values, _ -> values.any { it is ShizukuSetupModule.Result } }
        val result = collector.latestValues.filterIsInstance<ShizukuSetupModule.Result>().first()
        runBlocking { collector.cancelAndJoin() }
        return result
    }

    @Test fun `card package prefers the first manager that can be opened`() {
        // Shizuku+ next to its Compat Hub: the Hub owns the stock permission but has no launcher
        // activity, so opening it from the card would do nothing.
        coEvery { shizukuManager.getManagerId(any()) } returns shizukuPkg
        coEvery { shizukuManager.activeManagerIds(any()) } returns setOf(
            shizukuPkg,
            "af.shizuku.plus.api".toPkgId(),
        )
        every { packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api") } returns null
        every { packageManager.getLaunchIntentForPackage("af.shizuku.plus.api") } returns mockk<Intent>()

        firstResult(module()).pkg shouldBe "af.shizuku.plus.api".toPkgId()
    }

    @Test fun `card package falls back to the detected manager when none can be opened`() {
        coEvery { shizukuManager.getManagerId(any()) } returns shizukuPkg
        coEvery { shizukuManager.activeManagerIds(any()) } returns setOf(
            shizukuPkg,
            "af.shizuku.plus.api".toPkgId(),
        )
        every { packageManager.getLaunchIntentForPackage(any()) } returns null

        firstResult(module()).pkg shouldBe shizukuPkg
    }

    @Test fun `card package is the reference package when nothing is installed`() {
        coEvery { shizukuManager.getManagerId(any()) } returns null

        firstResult(module()).pkg shouldBe shizukuPkg

        verify(exactly = 0) { packageManager.getLaunchIntentForPackage(any()) }
    }

    @Test fun `the open target never leaves the active backend's family`() {
        // Porter is installed and openable, but this process talks to Shizuku. Sending the user to
        // Porter would open an app that cannot affect the link the card is waiting on.
        coEvery { shizukuManager.getManagerId(any()) } returns shizukuPkg
        coEvery { shizukuManager.activeManagerIds(any()) } returns setOf(shizukuPkg)
        coEvery { shizukuManager.managerIds() } returns setOf(shizukuPkg, porterPkg)
        every { packageManager.getLaunchIntentForPackage(any()) } returns mockk<Intent>()

        firstResult(module()).pkg shouldBe shizukuPkg
    }

    // --- backend -------------------------------------------------------------------------------

    @Test fun `the result carries the backend of the availability snapshot`() {
        coEvery { shizukuManager.availability() } returns AdbAvailability.Installed(
            backend = AdbBackend.PORTER,
            packageName = porterPkg.name,
            connected = true,
        )

        firstResult(module()).backend shouldBe AdbBackend.PORTER
    }

    @Test fun `every lookup uses the backend of one availability snapshot`() {
        coEvery { shizukuManager.availability() } returns AdbAvailability.Installed(
            backend = AdbBackend.PORTER,
            packageName = porterPkg.name,
            connected = false,
        )
        coEvery { shizukuManager.getManagerId(any()) } returns null

        firstResult(module())

        coVerify { shizukuManager.getManagerId(AdbBackend.PORTER) }
        coVerify { shizukuManager.referenceManagerId(AdbBackend.PORTER) }
        coVerify { shizukuManager.priorityBlockedManagerId(AdbBackend.PORTER) }
        coVerify(exactly = 0) { shizukuManager.activeBackend() }
    }

    private fun stubPorterInstalledNotConnected() {
        coEvery { shizukuManager.availability() } coAnswers {
            AdbAvailability.Installed(
                backend = AdbBackend.PORTER,
                packageName = porterPkg.name,
                connected = linkFlow.value != null,
            )
        }
        coEvery { shizukuManager.getManagerId(any()) } returns porterPkg
        coEvery { shizukuManager.activeManagerIds(any()) } returns setOf(porterPkg)
        coEvery { shizukuManager.priorityBlockedManagerId(any()) } coAnswers {
            if (linkFlow.value == null) shizukuPkg else null
        }
    }

    @Test fun `blockedManager names the Shizuku manager an installed Porter takes priority over`() {
        stubPorterInstalledNotConnected()

        val result = firstResult(module())
        result.isInstalled shouldBe true
        result.pkg shouldBe porterPkg
        result.backend shouldBe AdbBackend.PORTER
        result.blockedManager shouldBe shizukuPkg
    }

    @Test fun `blockedManager is null when nothing is blocked`() {
        firstResult(module()).blockedManager shouldBe null
    }

    @Test fun `Porter starting clears the priority hint while the state is collected`() {
        stubPorterInstalledNotConnected()
        val mod = module()

        val collector = mod.state.test(tag = "porter-start", scope = scope)
        collector.await { _, latest ->
            latest is ShizukuSetupModule.Result && !latest.isChecking && latest.blockedManager == shizukuPkg
        }

        linkFlow.value = mockk<AdbLink>()

        val settled = collector.await { _, latest ->
            latest is ShizukuSetupModule.Result && !latest.isChecking && latest.blockedManager == null
        }.shouldBeInstanceOf<ShizukuSetupModule.Result>()
        settled.isInstalled shouldBe true

        runBlocking { collector.cancelAndJoin() }
    }

    @Test fun `an incompatible manager that is too old is reported`() {
        coEvery { shizukuManager.availability() } returns AdbAvailability.Incompatible(
            backend = AdbBackend.SHIZUKU,
            packageName = shizukuPkg.name,
            serverTooOld = true,
            clientTooOld = false,
        )
        coEvery { shizukuManager.getServiceState() } returns ShizukuServiceState.Unknown

        val collector = module().state.test(tag = "manager-too-old", scope = scope)
        val settled = collector.await { _, latest ->
            latest is ShizukuSetupModule.Result && !latest.isChecking
        }.shouldBeInstanceOf<ShizukuSetupModule.Result>()

        settled.managerTooOld shouldBe true
        settled.sdMaidTooOld shouldBe false
        settled.isInstalled shouldBe true
        settled.isComplete shouldBe false

        runBlocking { collector.cancelAndJoin() }
    }

    @Test fun `an incompatible manager that is too new for SD Maid is reported`() {
        coEvery { shizukuManager.availability() } returns AdbAvailability.Incompatible(
            backend = AdbBackend.SHIZUKU,
            packageName = shizukuPkg.name,
            serverTooOld = false,
            clientTooOld = true,
        )
        coEvery { shizukuManager.getServiceState() } returns ShizukuServiceState.Unknown

        val collector = module().state.test(tag = "sdmaid-too-old", scope = scope)
        val settled = collector.await { _, latest ->
            latest is ShizukuSetupModule.Result && !latest.isChecking
        }.shouldBeInstanceOf<ShizukuSetupModule.Result>()

        settled.managerTooOld shouldBe false
        settled.sdMaidTooOld shouldBe true
        settled.isComplete shouldBe false

        runBlocking { collector.cancelAndJoin() }
    }

    // Real PackageInfo/ApplicationInfo constructors hit the stubbed android.jar, so build them the
    // way the Intent above is built and write the public fields getLabel2() reads.
    private fun stubLabel(label: String) {
        val appInfo = mockk<ApplicationInfo>(relaxed = true).apply {
            labelRes = 0
            nonLocalizedLabel = label
        }
        val info = mockk<PackageInfo>(relaxed = true).apply { applicationInfo = appInfo }
        every { packageManager.getPackageInfo(any<String>(), any<Int>()) } returns info
    }

    @Test fun `managerLabel is what the installed app calls itself`() {
        stubLabel("Shizuku+")

        firstResult(module()).managerLabel shouldBe "Shizuku+"
    }

    @Test fun `managerLabel is absent when no manager is installed`() {
        coEvery { shizukuManager.getManagerId(any()) } returns null
        coEvery { shizukuManager.activeManagerIds(any()) } returns emptySet()
        stubLabel("Shizuku")

        // The reference package fills Result.pkg, but nothing is installed, so there is no name to
        // report and the card must fall back to the backend label.
        firstResult(module()).managerLabel shouldBe null
    }

    @Test fun `blockedManagerLabel names the ignored app`() {
        stubPorterInstalledNotConnected()
        stubLabel("Shizuku")

        firstResult(module()).blockedManagerLabel shouldBe "Shizuku"
    }

    @Test fun `a blank label is treated as no label`() {
        stubLabel("   ")

        firstResult(module()).managerLabel shouldBe null
    }

    @Test fun `a failing label lookup leaves the flow usable`() {
        // getLabel2() only converts NameNotFoundException; anything else would escape into the
        // sharing coroutine, which no later refresh could revive.
        every { packageManager.getPackageInfo(any<String>(), any<Int>()) } throws RuntimeException("PM died")
        val mod = module()

        val collector = mod.state.test(tag = "first", scope = scope)
        collector.await { values, _ -> values.any { it is ShizukuSetupModule.Result } }
        collector.latestValues.last().shouldBeInstanceOf<ShizukuSetupModule.Result>().managerLabel shouldBe null

        val before = probeCount
        runBlocking { mod.refresh() }
        collector.await { _, _ -> probeCount > before }
        probeCount shouldBeGreaterThan before

        runBlocking { collector.cancelAndJoin() }
    }

    // --- permission ----------------------------------------------------------------------------

    @Test fun `toggle leaves the setting undecided when the permission request fails`() {
        useShizukuFlow.value = null
        coEvery { shizukuManager.isGranted() } returns false
        coEvery { shizukuManager.requestPermission() } returns null
        val mod = module()

        // Far below the 30s grant wait: the answer is taken from the request itself.
        runBlocking { withTimeout(5_000) { mod.toggleUseShizuku(true) } }

        useShizukuFlow.value shouldBe null
        coVerify(exactly = 1) { shizukuManager.requestPermission() }
        coVerify(exactly = 1) { useShizukuValue.update(any()) }
    }

    @Test fun `the state never asks for permission on its own`() {
        // The manager's prompt closing resumes the Setup screen, which refreshes: a request issued
        // from here would re-open the prompt every time it is dismissed.
        coEvery { shizukuManager.isGranted() } returns false
        coEvery { shizukuManager.getServiceState() } coAnswers {
            probeCount++
            ShizukuServiceState.PermissionDenied(permanently = false)
        }
        linkFlow.value = mockk<AdbLink>()
        val mod = module()

        val collector = mod.state.test(tag = "no-auto-request", scope = scope)
        collector.await { _, latest -> latest is ShizukuSetupModule.Result && !latest.isChecking }

        val beforeLink = probeCount
        linkFlow.value = mockk<AdbLink>()
        collector.await { _, _ -> probeCount > beforeLink }

        repeat(3) {
            val before = probeCount
            runBlocking { mod.refresh() }
            collector.await { _, _ -> probeCount > before }
        }
        runBlocking { delay(50) }

        coVerify(exactly = 0) { shizukuManager.requestPermission() }

        runBlocking { collector.cancelAndJoin() }
    }

    @Test fun `grantAccess asks once and re-probes`() {
        coEvery { shizukuManager.isGranted() } returns false
        coEvery { shizukuManager.requestPermission() } returns AdbPermission.DENIED
        val mod = module()

        val collector = mod.state.test(tag = "grant-access", scope = scope)
        collector.await { _, latest -> latest is ShizukuSetupModule.Result && !latest.isChecking }

        val before = probeCount
        runBlocking { withTimeout(5_000) { mod.grantAccess() } }
        collector.await { _, _ -> probeCount > before }

        probeCount shouldBeGreaterThan before
        coVerify(exactly = 1) { shizukuManager.requestPermission() }

        runBlocking { collector.cancelAndJoin() }
    }

    @Test fun `grantAccess joins a request that is already open`() {
        coEvery { shizukuManager.isGranted() } returns false
        val answer = CompletableDeferred<AdbPermission?>()
        var requests = 0
        coEvery { shizukuManager.requestPermission() } coAnswers {
            requests++
            answer.await()
        }
        val mod = module()

        val first = scope.async { mod.grantAccess() }
        val second = scope.async { mod.grantAccess() }

        requests shouldBe 1

        answer.complete(AdbPermission.GRANTED)
        runBlocking {
            withTimeout(5_000) {
                first.await()
                second.await()
            }
        }

        coVerify(exactly = 1) { shizukuManager.requestPermission() }
    }

    private fun toggleWithAnswer(answer: AdbPermission) {
        useShizukuFlow.value = null
        coEvery { shizukuManager.isGranted() } returns false
        coEvery { shizukuManager.requestPermission() } returns answer
        // Already attached, so the post-toggle link wait ends at once.
        linkFlow.value = mockk<AdbLink>()
        val mod = module()

        runBlocking { withTimeout(5_000) { mod.toggleUseShizuku(true) } }
    }

    @Test fun `toggle keeps the option selected when the manager denies`() {
        toggleWithAnswer(AdbPermission.DENIED)

        useShizukuFlow.value shouldBe true
    }

    @Test fun `toggle keeps the option selected when the manager denies permanently`() {
        toggleWithAnswer(AdbPermission.DENIED_PERMANENTLY)

        useShizukuFlow.value shouldBe true
    }

    @Test fun `toggle re-probes when the answer changes nothing upstream`() {
        // Re-selecting the selected option stores the same value, and DENIED turning into
        // DENIED_PERMANENTLY does not change the granted flag, so nothing upstream emits.
        coEvery { shizukuManager.isGranted() } returns false
        var serviceState: ShizukuServiceState = ShizukuServiceState.PermissionDenied(permanently = false)
        coEvery { shizukuManager.getServiceState() } coAnswers { probeCount++; serviceState }
        coEvery { shizukuManager.requestPermission() } returns AdbPermission.DENIED_PERMANENTLY
        linkFlow.value = mockk<AdbLink>()
        val mod = module()

        val collector = mod.state.test(tag = "toggle-reprobe", scope = scope)
        collector.await { _, latest ->
            latest is ShizukuSetupModule.Result &&
                !latest.isChecking &&
                latest.serviceState == ShizukuServiceState.PermissionDenied(permanently = false)
        }

        serviceState = ShizukuServiceState.PermissionDenied(permanently = true)
        val before = probeCount
        runBlocking { withTimeout(5_000) { mod.toggleUseShizuku(true) } }

        val settled = collector.await { _, latest ->
            latest is ShizukuSetupModule.Result &&
                !latest.isChecking &&
                latest.serviceState == ShizukuServiceState.PermissionDenied(permanently = true)
        }.shouldBeInstanceOf<ShizukuSetupModule.Result>()

        probeCount shouldBeGreaterThan before
        settled.serviceState shouldBe ShizukuServiceState.PermissionDenied(permanently = true)
        useShizukuFlow.value shouldBe true

        runBlocking { collector.cancelAndJoin() }
    }

    @Test fun `toggle re-prompts after an unanswered request timed out`() = runTest {
        coEvery { shizukuManager.isGranted() } returns false
        coEvery { shizukuManager.requestPermission() } coAnswers { awaitCancellation() }
        val mod = module(moduleScope = backgroundScope)

        mod.toggleUseShizuku(true)
        currentTime shouldBeGreaterThanOrEqual 30_000L

        mod.toggleUseShizuku(true)
        currentTime shouldBeGreaterThanOrEqual 60_000L

        coVerify(exactly = 2) { shizukuManager.requestPermission() }
    }

    @Test fun `the shared request is bounded for every caller and survives the first one leaving`() = runTest {
        coEvery { shizukuManager.isGranted() } returns false
        coEvery { shizukuManager.requestPermission() } coAnswers { awaitCancellation() }
        val mod = module(moduleScope = backgroundScope)

        val first = launch { mod.grantAccess() }
        runCurrent()
        advanceTimeBy(20_000)
        // E.g. the Setup screen's ViewModel being cleared while the prompt is still open.
        first.cancel()

        val joiner = async { mod.toggleUseShizuku(true) }
        joiner.await()

        // The joiner shares the first request's bound instead of starting its own 30s.
        currentTime shouldBeGreaterThanOrEqual ShizukuSetupModule.PERMISSION_REQUEST_TIMEOUT_MS
        currentTime shouldBeLessThan 50_000L
        useShizukuFlow.value shouldBe null
        first.isCancelled shouldBe true
        coVerify(exactly = 1) { shizukuManager.requestPermission() }

        mod.grantAccess()

        coVerify(exactly = 2) { shizukuManager.requestPermission() }
    }

    // --- data areas ----------------------------------------------------------------------------

    @Test fun `a settled change of ADB access reloads the data areas once`() {
        var serviceState: ShizukuServiceState = ShizukuServiceState.PermissionDenied(permanently = false)
        coEvery { shizukuManager.getServiceState() } coAnswers { probeCount++; serviceState }
        val mod = module()

        val collector = mod.state.test(tag = "reload", scope = scope)
        collector.await { _, latest -> latest is ShizukuSetupModule.Result && !latest.isChecking }

        // The first observation is not a change.
        coVerify(exactly = 0) { dataAreaManager.reload() }

        serviceState = ShizukuServiceState.Available
        runBlocking { mod.refresh() }
        collector.await { _, latest ->
            latest is ShizukuSetupModule.Result && !latest.isChecking && latest.ourService
        }

        coVerify(exactly = 1) { dataAreaManager.reload() }

        runBlocking { collector.cancelAndJoin() }
    }
}
