package eu.darken.sdmse.corpsefinder.core

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.forensics.OwnerInfo
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.common.user.UserManager2
import eu.darken.sdmse.common.user.UserProfile2
import eu.darken.sdmse.corpsefinder.core.filter.CorpseFilter
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderDeleteTask
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderOneClickTask
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderScanTask
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderSchedulerTask
import eu.darken.sdmse.corpsefinder.core.tasks.UninstallWatcherTask
import eu.darken.sdmse.corpsefinder.core.watcher.ExternalWatcherResult
import eu.darken.sdmse.corpsefinder.core.watcher.WatcherNotifications
import eu.darken.sdmse.corpsefinder.ui.preview.previewCorpse
import eu.darken.sdmse.corpsefinder.ui.preview.previewLocalPathLookup
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import eu.darken.sdmse.exclusion.core.types.PkgExclusion
import eu.darken.sdmse.exclusion.core.types.UserExclusion
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.setup.SetupHeartbeat
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.plus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2

class CorpseFinderTest : BaseTest() {

    // CorpseFinder's `submit()` wraps work in `keepResourceHoldersAlive(fileForensics,
    // gatewaySwitch, pkgOps)`, which calls `addChild(sharedResource)` + `sharedResource.get()`
    // on each. Plain MockK mocks would fail at those calls — so we wire each dependency to a
    // real `SharedResource.createKeepAlive(...)` backed by a long-lived scope.
    private val keepAliveScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @AfterEach
    fun stopKeepAliveScope() {
        keepAliveScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    private fun areaInfoForUser(userHandle: UserHandle2): AreaInfo {
        val root = LocalPath.build("storage", "emulated", "0", "Android", "data")
        return AreaInfo(
            file = root,
            prefix = root,
            dataArea = DataArea(
                path = root,
                type = DataArea.Type.PRIVATE_DATA,
                label = "Private data".toCaString(),
                userHandle = userHandle,
            ),
            isBlackListLocation = false,
        )
    }

    private fun corpse(
        name: String,
        size: Long,
        ownerPkgId: Pkg.Id = Pkg.Id(name = "com.example.app"),
        userHandle: UserHandle2 = UserHandle2(handleId = 0),
    ): Corpse = previewCorpse(
        lookup = previewLocalPathLookup(
            pathSegments = arrayOf("storage", "emulated", "0", "Android", "data", name),
            size = size,
        ),
        content = emptyList(),
    ).copy(
        // Override BOTH areaInfo and owner userHandle. AreaInfo.userHandle (which is
        // dataArea.userHandle) drives the multi-user filter; Owner.userHandle drives the
        // pkgOps.isInstalleMaybe lookup. They need to match for system-user tests.
        ownerInfo = OwnerInfo(
            areaInfo = areaInfoForUser(userHandle),
            owners = setOf(
                eu.darken.sdmse.common.forensics.Owner(pkgId = ownerPkgId, userHandle = userHandle),
            ),
            installedOwners = emptySet(),
            hasUnknownOwner = false,
        ),
    )

    private fun setupFinder(
        useRoot: Boolean = false,
        isWatcherEnabled: Boolean = false,
        filterFactories: Set<CorpseFilter.Factory> = emptySet(),
        userHasMultiUserSupport: Boolean = false,
        exclusionHolders: List<UserExclusion> = emptyList(),
        savedExclusions: Collection<Exclusion> = emptyList(),
    ): Setup {
        val fileForensics = mockk<FileForensics>().apply {
            every { sharedResource } returns SharedResource.createKeepAlive("ff", keepAliveScope)
        }
        val gatewaySwitch = mockk<GatewaySwitch>().apply {
            every { sharedResource } returns SharedResource.createKeepAlive("gw", keepAliveScope)
        }
        val pkgOps = mockk<PkgOps>().apply {
            every { sharedResource } returns SharedResource.createKeepAlive("po", keepAliveScope)
        }
        val watcherNotifications = mockk<WatcherNotifications>(relaxed = true)
        val rootManager = mockk<RootManager>().apply {
            every { this@apply.useRoot } returns flowOf(useRoot)
        }
        val settings = mockk<CorpseFinderSettings>().apply {
            every { this@apply.isWatcherEnabled } returns mockk {
                every { flow } returns flowOf(isWatcherEnabled)
            }
        }
        val exclusionManager = mockk<ExclusionManager>().apply {
            every { exclusions } returns flowOf(exclusionHolders)
            coEvery { save(any()) } returns savedExclusions
            coJustRun { remove(any()) }
        }
        val userManager = mockk<UserManager2>().apply {
            every { hasMultiUserSupport } returns userHasMultiUserSupport
            coEvery { systemUser() } returns UserProfile2(handle = UserHandle2(handleId = -1))
        }
        val setupHeartbeat = SetupHeartbeat { /* no-op: setup is considered complete */ }

        val finder = CorpseFinder(
            appScope = keepAliveScope,
            filterFactories = filterFactories,
            fileForensics = fileForensics,
            gatewaySwitch = gatewaySwitch,
            exclusionManager = exclusionManager,
            userManager = userManager,
            pkgOps = pkgOps,
            watcherNotifications = watcherNotifications,
            rootManager = rootManager,
            settings = settings,
            inventorySetupCheck = setupHeartbeat,
        )
        return Setup(
            finder = finder,
            exclusionManager = exclusionManager,
            watcherNotifications = watcherNotifications,
            pkgOps = pkgOps,
        )
    }

    private class Setup(
        val finder: CorpseFinder,
        val exclusionManager: ExclusionManager,
        val watcherNotifications: WatcherNotifications,
        val pkgOps: PkgOps,
    )

    private fun fakeFactory(
        enabled: Boolean,
        produces: Collection<Corpse>,
    ): CorpseFilter.Factory = fakeFactory(enabled, producesProvider = { produces })

    private class CountingFactory(
        private val enabled: Boolean,
        private val producesProvider: () -> Collection<Corpse>,
    ) : CorpseFilter.Factory {
        var scanInvocations: Int = 0
            private set

        override suspend fun isEnabled(): Boolean = enabled

        override suspend fun create(): CorpseFilter = object : CorpseFilter(
            tag = "TestFilter",
            defaultProgress = eu.darken.sdmse.common.progress.Progress.Data(),
        ) {
            override suspend fun doScan(): Collection<Corpse> {
                scanInvocations++
                return producesProvider()
            }
        }
    }

    private fun fakeFactory(
        enabled: Boolean,
        producesProvider: () -> Collection<Corpse>,
    ): CountingFactory = CountingFactory(enabled, producesProvider)

    private suspend fun CorpseFinder.dataFromState(): CorpseFinder.Data? =
        state.map { it.data }.first()

    // ─────────────────────────── scan + dispatch tests ───────────────────────────

    @Test
    fun `submit ScanTask with no factories yields empty Success`() = runTest2 {
        val setup = setupFinder()

        val result = setup.finder.submit(CorpseFinderScanTask())

        result.shouldBeInstanceOf<CorpseFinderScanTask.Success>()
        setup.finder.dataFromState()!!.corpses shouldBe emptyList()
    }

    @Test
    fun `submit ScanTask runs enabled factories and populates internal data`() = runTest2 {
        val a = corpse("a", 100)
        val b = corpse("b", 200)
        val setup = setupFinder(
            filterFactories = setOf(
                fakeFactory(enabled = true, produces = listOf(a, b)),
            ),
        )

        setup.finder.submit(CorpseFinderScanTask())

        val data = setup.finder.dataFromState()!!
        data.corpses.toList() shouldContainExactlyInAnyOrder listOf(a, b)
    }

    @Test
    fun `submit ScanTask skips disabled factories`() = runTest2 {
        val ran = corpse("ran", 100)
        val skipped = corpse("skipped", 200)
        val setup = setupFinder(
            filterFactories = setOf(
                fakeFactory(enabled = true, produces = listOf(ran)),
                fakeFactory(enabled = false, produces = listOf(skipped)),
            ),
        )

        setup.finder.submit(CorpseFinderScanTask())

        val data = setup.finder.dataFromState()!!
        data.corpses.map { it.identifier } shouldContainExactlyInAnyOrder listOf(ran.identifier)
    }

    @Test
    fun `submit ScanTask filters out corpses owned by pkg-excluded packages`() = runTest2 {
        val excludedPkg = Pkg.Id(name = "com.excluded.app")
        val keptPkg = Pkg.Id(name = "com.kept.app")
        val excluded = corpse("excluded", 100, ownerPkgId = excludedPkg)
        val kept = corpse("kept", 200, ownerPkgId = keptPkg)
        val setup = setupFinder(
            filterFactories = setOf(
                fakeFactory(enabled = true, produces = listOf(excluded, kept)),
            ),
            exclusionHolders = listOf(
                UserExclusion(
                    PkgExclusion(
                        pkgId = excludedPkg,
                        tags = setOf(Exclusion.Tag.CORPSEFINDER),
                    ),
                ),
            ),
        )

        setup.finder.submit(CorpseFinderScanTask())

        val data = setup.finder.dataFromState()!!
        data.corpses.map { it.identifier } shouldContainExactlyInAnyOrder listOf(kept.identifier)
    }

    @Test
    fun `submit ScanTask filters out corpses at path-excluded locations`() = runTest2 {
        val excludedCorpse = corpse("excluded-by-path", 100)
        val keptCorpse = corpse("kept-by-path", 200)
        val setup = setupFinder(
            filterFactories = setOf(
                fakeFactory(enabled = true, produces = listOf(excludedCorpse, keptCorpse)),
            ),
            exclusionHolders = listOf(
                UserExclusion(
                    PathExclusion(
                        path = excludedCorpse.lookup.lookedUp,
                        tags = setOf(Exclusion.Tag.CORPSEFINDER),
                    ),
                ),
            ),
        )

        setup.finder.submit(CorpseFinderScanTask())

        val data = setup.finder.dataFromState()!!
        data.corpses.map { it.identifier } shouldContainExactlyInAnyOrder listOf(keptCorpse.identifier)
    }

    @Test
    fun `submit ScanTask drops system-user corpse when owner package is installed - multi-user false positive`() = runTest2 {
        // The multi-user guard at CorpseFinder.kt:233 only triggers when:
        //   - hasMultiUserSupport == true,
        //   - corpse.areaInfo.userHandle == systemUser() (handleId = -1),
        //   - at least one owner package is `isInstalleMaybe == true`.
        // Other paths short-circuit `return@filter true` (keep). Without this test the most
        // commonly broken branch would be silently uncovered.
        val ownerPkg = Pkg.Id(name = "com.installed")
        val systemUserHandle = UserHandle2(handleId = -1)
        val systemUserCorpse = corpse(
            name = "system-user-falsepositive",
            size = 100,
            ownerPkgId = ownerPkg,
            userHandle = systemUserHandle,
        )
        val factory = fakeFactory(enabled = true, producesProvider = { listOf(systemUserCorpse) })
        val setup = setupFinder(
            filterFactories = setOf(factory),
            userHasMultiUserSupport = true,
        )
        coEvery { setup.pkgOps.isInstalleMaybe(ownerPkg, systemUserHandle) } returns true

        setup.finder.submit(CorpseFinderScanTask())

        // Corpse dropped: owner pkg is installed for the system user → likely a multi-user
        // false positive.
        setup.finder.dataFromState()!!.corpses shouldBe emptyList()
    }

    @Test
    fun `submit ScanTask keeps secondary-user corpse even when owner package is installed`() = runTest2 {
        // The same combination but with the corpse in a non-system user MUST short-circuit
        // to `return@filter true` BEFORE the owner-installed check fires. This pins the
        // direction of the multi-user filter (see Codex review).
        val ownerPkg = Pkg.Id(name = "com.installed")
        val secondaryUserHandle = UserHandle2(handleId = 10)
        val secondaryUserCorpse = corpse(
            name = "secondary-user",
            size = 100,
            ownerPkgId = ownerPkg,
            userHandle = secondaryUserHandle,
        )
        val factory = fakeFactory(enabled = true, producesProvider = { listOf(secondaryUserCorpse) })
        val setup = setupFinder(
            filterFactories = setOf(factory),
            userHasMultiUserSupport = true,
        )
        // isInstalleMaybe would NOT be reached because of the early `return@filter true`. Stub
        // it anyway so a regression that flipped the direction (and DID call it) doesn't blow
        // up with a "no answer found" exception — it'd just answer `true` and fail the assert.
        coEvery { setup.pkgOps.isInstalleMaybe(ownerPkg, secondaryUserHandle) } returns true

        setup.finder.submit(CorpseFinderScanTask())

        setup.finder.dataFromState()!!.corpses.map { it.identifier } shouldBe listOf(secondaryUserCorpse.identifier)
    }

    @Test
    fun `submit ScanTask pkgIdFilter limits results to corpses owning the targeted packages`() = runTest2 {
        // Regression test for what used to be FIXME(corpsefinder-pkgIdFilter-dead-code): the
        // field is now consumed in performScan as a post-filter. Filters still all run, but
        // results that don't own any of the targeted packages are discarded.
        val target = Pkg.Id(name = "com.target")
        val unrelated = Pkg.Id(name = "com.unrelated")
        val targetCorpse = corpse("target", 100, ownerPkgId = target)
        val unrelatedCorpse = corpse("unrelated", 200, ownerPkgId = unrelated)

        val setup = setupFinder(
            filterFactories = setOf(
                fakeFactory(enabled = true, produces = listOf(targetCorpse, unrelatedCorpse)),
            ),
        )

        setup.finder.submit(CorpseFinderScanTask(pkgIdFilter = setOf(target)))

        val data = setup.finder.dataFromState()!!
        // Only the corpse owning `target` is kept; unrelated owner is filtered out.
        data.corpses.map { it.identifier } shouldBe listOf(targetCorpse.identifier)
    }

    @Test
    fun `submit ScanTask empty pkgIdFilter keeps all corpses`() = runTest2 {
        // Defends the "empty filter = no filtering" semantics that all non-watcher callers
        // depend on. Inverting the conditional in performScan would silently break every
        // delete/scan path.
        val a = corpse("a", 100)
        val b = corpse("b", 200)
        val setup = setupFinder(
            filterFactories = setOf(
                fakeFactory(enabled = true, producesProvider = { listOf(a, b) }),
            ),
        )

        setup.finder.submit(CorpseFinderScanTask(pkgIdFilter = emptySet()))

        setup.finder.dataFromState()!!.corpses.map { it.identifier } shouldContainExactlyInAnyOrder listOf(
            a.identifier, b.identifier,
        )
    }

    @Test
    fun `discardScanData clears the scan results`() = runTest2 {
        val a = corpse("a", 100)
        val setup = setupFinder(
            filterFactories = setOf(fakeFactory(enabled = true, produces = listOf(a))),
        )
        setup.finder.submit(CorpseFinderScanTask())
        setup.finder.dataFromState()!!.corpses.map { it.identifier } shouldBe listOf(a.identifier)

        setup.finder.discardScanData()

        setup.finder.dataFromState() shouldBe null
    }

    // ─────────────────────────── task contract ───────────────────────────

    @Test
    fun `CorpseFinderDeleteTask rejects targetContent without targetCorpses`() {
        val anyPath = LocalPath.build("storage", "emulated", "0", "Android", "data", "x")
        val ex = runCatching {
            CorpseFinderDeleteTask(
                targetCorpses = null,
                targetContent = setOf(anyPath),
            )
        }.exceptionOrNull()
        // The init { require(...) } block throws IllegalArgumentException at construction —
        // production callers never produce this shape, but the contract protects future ones
        // from reintroducing the cross-corpse smear bug that was just fixed.
        ex.shouldBeInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `CorpseFinderDeleteTask accepts targetContent paired with non-null targetCorpses`() {
        val anyPath = LocalPath.build("storage", "emulated", "0", "Android", "data", "x")
        // No exception — the matched-pair case is the one all real callers use.
        CorpseFinderDeleteTask(
            targetCorpses = setOf(anyPath),
            targetContent = setOf(anyPath),
        )
    }

    @Test
    fun `CorpseFinderDeleteTask accepts the delete-all defaults`() {
        // No exception — the dashboard's `delete everything` path constructs this shape.
        CorpseFinderDeleteTask()
    }

    // ─────────────────────────── exclude / undoExclude ───────────────────────────

    @Test
    fun `exclude saves PathExclusion with CORPSEFINDER tag and returns ExclusionUndo`() = runTest2 {
        val target = corpse("target", 100)
        val other = corpse("other", 200)
        val setup = setupFinder(
            filterFactories = setOf(
                fakeFactory(enabled = true, produces = listOf(target, other)),
            ),
        )
        setup.finder.submit(CorpseFinderScanTask())

        // Return a real-but-distinct exclusion id from save() so we can prove that
        // ExclusionUndo.exclusionIds comes from the SAVED set, not the requested set. (If the
        // production code accidentally pulled ids off the requested PathExclusion instead, the
        // assertion below would still pass with a generated id — the synthetic id catches it.)
        val savedExclusion = mockk<Exclusion>().apply {
            every { id } returns "saved-1"
        }
        val capturedSave = slot<Set<Exclusion>>()
        coEvery { setup.exclusionManager.save(capture(capturedSave)) } returns listOf(savedExclusion)

        val undo = setup.finder.exclude(setOf(target.identifier))

        capturedSave.captured.size shouldBe 1
        val saved = capturedSave.captured.single() as PathExclusion
        saved.path shouldBe target.lookup.lookedUp
        saved.tags shouldBe setOf(Exclusion.Tag.CORPSEFINDER)

        undo.exclusionIds shouldBe setOf("saved-1")
    }

    @Test
    fun `exclude removes excluded corpses from internal data`() = runTest2 {
        val target = corpse("target", 100)
        val keep = corpse("keep", 200)
        val setup = setupFinder(
            filterFactories = setOf(
                fakeFactory(enabled = true, produces = listOf(target, keep)),
            ),
        )
        setup.finder.submit(CorpseFinderScanTask())

        coEvery { setup.exclusionManager.save(any()) } returns listOf(
            PathExclusion(
                path = target.lookup.lookedUp,
                tags = setOf(Exclusion.Tag.CORPSEFINDER),
            ),
        )

        setup.finder.exclude(setOf(target.identifier))

        val data = setup.finder.dataFromState()!!
        data.corpses.map { it.identifier } shouldContainExactlyInAnyOrder listOf(keep.identifier)
    }

    @Test
    fun `undoExclude calls ExclusionManager remove exactly once with the same ids`() = runTest2 {
        val target = corpse("target", 100)
        val setup = setupFinder(
            filterFactories = setOf(
                fakeFactory(enabled = true, produces = listOf(target)),
            ),
        )
        setup.finder.submit(CorpseFinderScanTask())

        coEvery { setup.exclusionManager.save(any()) } returns listOf(
            PathExclusion(
                path = target.lookup.lookedUp,
                tags = setOf(Exclusion.Tag.CORPSEFINDER),
            ),
        )

        val undo = setup.finder.exclude(setOf(target.identifier))
        setup.finder.undoExclude(undo)

        coVerify(exactly = 1) { setup.exclusionManager.remove(undo.exclusionIds) }
        // No additional remove() calls — defends against a regression that double-removes or
        // also removes neighbouring exclusions.
        coVerify(exactly = 1) { setup.exclusionManager.remove(any()) }
    }

    @Test
    fun `undoExclude restores previousData when state has not moved on`() = runTest2 {
        val target = corpse("target", 100)
        val keep = corpse("keep", 200)
        val setup = setupFinder(
            filterFactories = setOf(
                fakeFactory(enabled = true, produces = listOf(target, keep)),
            ),
        )
        setup.finder.submit(CorpseFinderScanTask())

        coEvery { setup.exclusionManager.save(any()) } returns listOf(
            PathExclusion(
                path = target.lookup.lookedUp,
                tags = setOf(Exclusion.Tag.CORPSEFINDER),
            ),
        )

        val undo = setup.finder.exclude(setOf(target.identifier))

        // Sanity: target is removed before undo.
        setup.finder.dataFromState()!!.corpses.map { it.identifier } shouldBe listOf(keep.identifier)

        setup.finder.undoExclude(undo)

        // Both corpses are back.
        setup.finder.dataFromState()!!.corpses.map { it.identifier }
            .toSet() shouldBe setOf(target.identifier, keep.identifier)
    }

    @Test
    fun `undoExclude with stale handle removes ids but skips data restore when ref does not match`() = runTest2 {
        // First scan produces `target`, second produces a DIFFERENT corpse (`replacement`).
        // After exclude + rescan, internalData points at the second-scan Data, which has no
        // corpses in common with undo.previousData. A regression that always restored
        // previousData would resurrect `target` and fail the post-undo assertion.
        val target = corpse("target", 100)
        val replacement = corpse("replacement", 999)

        var scanRound = 0
        val factory = fakeFactory(
            enabled = true,
            producesProvider = {
                scanRound++
                if (scanRound == 1) listOf(target) else listOf(replacement)
            },
        )
        val setup = setupFinder(filterFactories = setOf(factory))
        setup.finder.submit(CorpseFinderScanTask())

        coEvery { setup.exclusionManager.save(any()) } returns listOf(
            PathExclusion(
                path = target.lookup.lookedUp,
                tags = setOf(Exclusion.Tag.CORPSEFINDER),
            ),
        )
        val undo = setup.finder.exclude(setOf(target.identifier))
        // undo.previousData contains `target`; postExcludeData is empty.

        setup.finder.submit(CorpseFinderScanTask())
        // Now internalData is a third Data object containing only `replacement` —
        // distinct from BOTH undo.previousData and undo.postExcludeData.

        setup.finder.undoExclude(undo)

        coVerify(exactly = 1) { setup.exclusionManager.remove(undo.exclusionIds) }
        // State after undoExclude must reflect the post-rescan snapshot (only replacement),
        // NOT undo.previousData (which contained target).
        setup.finder.dataFromState()!!.corpses.map { it.identifier } shouldBe listOf(replacement.identifier)
    }

    // ─────────────────────────── chained-task dispatch ───────────────────────────

    @Test
    fun `submit OneClickTask chains scan then delete and returns OneClickTask Success`() = runTest2 {
        // Use a counting factory so we can prove the SCAN step actually ran (and wasn't
        // skipped by a no-op dispatcher).
        val factory = fakeFactory(enabled = true, producesProvider = { emptyList() })
        val setup = setupFinder(filterFactories = setOf(factory))

        val result = setup.finder.submit(CorpseFinderOneClickTask())

        result.shouldBeInstanceOf<CorpseFinderOneClickTask.Success>()
        result.affectedSpace shouldBe 0L
        result.affectedPaths shouldBe emptySet()
        factory.scanInvocations shouldBe 1
        // dataFromState must be non-null after a successful scan-then-delete cycle.
        setup.finder.dataFromState() shouldBe CorpseFinder.Data(
            corpses = emptyList(),
            lastResult = result,
        )
    }

    @Test
    fun `submit SchedulerTask chains scan then delete and returns SchedulerTask Success`() = runTest2 {
        val factory = fakeFactory(enabled = true, producesProvider = { emptyList() })
        val setup = setupFinder(filterFactories = setOf(factory))

        val result = setup.finder.submit(CorpseFinderSchedulerTask(scheduleId = "test-schedule"))

        result.shouldBeInstanceOf<CorpseFinderSchedulerTask.Success>()
        result.affectedSpace shouldBe 0L
        result.affectedPaths shouldBe emptySet()
        factory.scanInvocations shouldBe 1
        setup.finder.dataFromState() shouldBe CorpseFinder.Data(
            corpses = emptyList(),
            lastResult = result,
        )
    }

    @Test
    fun `submit UninstallWatcherTask with autoDelete false and matches emits scan notification only`() = runTest2 {
        val targetPkg = Pkg.Id(name = "com.target")
        val unrelatedPkg = Pkg.Id(name = "com.unrelated")
        val matching = corpse("matching", 100, ownerPkgId = targetPkg)
        val unrelated = corpse("unrelated", 50, ownerPkgId = unrelatedPkg)
        val setup = setupFinder(
            filterFactories = setOf(
                fakeFactory(enabled = true, produces = listOf(matching, unrelated)),
            ),
        )

        val result = setup.finder.submit(
            UninstallWatcherTask(target = targetPkg, autoDelete = false),
        )

        result.shouldBeInstanceOf<UninstallWatcherTask.Success>()
        // Only the matching corpse is considered "found" by the watcher path.
        result.affectedPaths shouldBe emptySet()  // autoDelete=false → nothing deleted
        result.affectedSpace shouldBe 0L

        // Scan notification fires because targets.isNotEmpty() (matching corpse owns targetPkg).
        val capturedScan = slot<ExternalWatcherResult.Scan>()
        coVerify(exactly = 1) {
            setup.watcherNotifications.notifyOfScan(capture(capturedScan))
        }
        capturedScan.captured.pkgId shouldBe targetPkg
        capturedScan.captured.foundItems shouldBe 1

        // No deletion notification.
        coVerify(exactly = 0) { setup.watcherNotifications.notifyOfDeletion(any()) }
    }

    @Test
    fun `submit UninstallWatcherTask with no matching corpses emits no notifications`() = runTest2 {
        val targetPkg = Pkg.Id(name = "com.target")
        val unrelatedPkg = Pkg.Id(name = "com.unrelated")
        val unrelated = corpse("unrelated", 100, ownerPkgId = unrelatedPkg)
        val factory = fakeFactory(enabled = true, producesProvider = { listOf(unrelated) })
        val setup = setupFinder(filterFactories = setOf(factory))

        val result = setup.finder.submit(
            UninstallWatcherTask(target = targetPkg, autoDelete = false),
        )

        result.shouldBeInstanceOf<UninstallWatcherTask.Success>()
        factory.scanInvocations shouldBe 1
        // Pre-watcher state for this test was "null" (no prior user scan). The watcher uses
        // runScan + reconcile, so internalData remains untouched — `null` here means the
        // user-driven scan was never run, NOT that the watcher wiped it.
        setup.finder.dataFromState() shouldBe null

        coVerify(exactly = 0) { setup.watcherNotifications.notifyOfScan(any()) }
        coVerify(exactly = 0) { setup.watcherNotifications.notifyOfDeletion(any()) }
    }

    @Test
    fun `submit UninstallWatcherTask preserves the user's prior lastResult`() = runTest2 {
        // Regression: UninstallWatcherTask is a background event and must not overwrite the
        // dashboard-visible `lastResult` field with the watcher's success — otherwise the
        // dashboard would advertise the watcher as the user's last action, replacing whatever
        // the user actually saw last (e.g. "X corpses found" from their previous scan).
        val targetPkg = Pkg.Id(name = "com.target")
        val unrelatedPkg = Pkg.Id(name = "com.unrelated")
        val userCorpse = corpse("user", 100, ownerPkgId = unrelatedPkg)
        val factory = fakeFactory(enabled = true, producesProvider = { listOf(userCorpse) })
        val setup = setupFinder(filterFactories = setOf(factory))

        // User scan: lastResult = CorpseFinderScanTask.Success.
        val userResult = setup.finder.submit(CorpseFinderScanTask())
        userResult.shouldBeInstanceOf<CorpseFinderScanTask.Success>()
        setup.finder.dataFromState()!!.lastResult shouldBe userResult

        // Watcher event: must not overwrite lastResult.
        setup.finder.submit(
            UninstallWatcherTask(target = targetPkg, autoDelete = false),
        )

        setup.finder.dataFromState()!!.lastResult shouldBe userResult
    }

    @Test
    fun `submit UninstallWatcherTask does not disturb the user's existing scan results`() = runTest2 {
        // Regression for the bug Codex flagged after fixing pkgIdFilter: a watcher event for
        // an unrelated package must NOT replace the user's full corpse list with the filtered
        // watcher scan (which would also trigger the data-drain navUp in the UI).
        val targetPkg = Pkg.Id(name = "com.target")
        val unrelatedPkg = Pkg.Id(name = "com.unrelated")
        val userCorpseA = corpse("user-a", 100, ownerPkgId = unrelatedPkg)
        val userCorpseB = corpse("user-b", 200, ownerPkgId = unrelatedPkg)

        // The factory returns both unrelated corpses on every scan. The first call (user scan)
        // populates internalData with both. The second call (watcher scan) is filtered by
        // pkgIdFilter to corpses owning targetPkg — i.e. zero results.
        val factory = fakeFactory(
            enabled = true,
            producesProvider = { listOf(userCorpseA, userCorpseB) },
        )
        val setup = setupFinder(filterFactories = setOf(factory))

        // Establish a user-driven scan first.
        setup.finder.submit(CorpseFinderScanTask())
        val beforeWatcher = setup.finder.dataFromState()!!.corpses.map { it.identifier }.toSet()
        beforeWatcher shouldBe setOf(userCorpseA.identifier, userCorpseB.identifier)

        // Watcher fires for a different pkg, autoDelete off.
        setup.finder.submit(
            UninstallWatcherTask(target = targetPkg, autoDelete = false),
        )

        // The user's view is untouched: both unrelated corpses still present.
        setup.finder.dataFromState()!!.corpses.map { it.identifier }
            .toSet() shouldBe beforeWatcher
    }

    // ─────────────────────────── delete-path coverage gap ───────────────────────────

    // NOTE: Tests that exercise the actual delete path (CorpseFinderDeleteTask success with
    // non-empty targets, UninstallWatcherTask with autoDelete=true, OneClick/Scheduler with
    // non-empty scans) would require stubbing the suspend extension `APath.delete(GatewaySwitch)`
    // and several `APathLookup` properties through mockkStatic. The complexity outweighs the
    // payoff for this PR. Three pre-existing bugs in the delete path were surfaced during
    // planning:
    //   1) targetContent paths are deleted BEFORE intersecting with corpse.content (defense
    //      in depth gap — VM is currently the only guard)
    //   2) targetCorpses==null + targetContent!=null smears across every corpse
    //   3) WriteException for one path doesn't abort the rest (intended but uncovered)
    // These are documented for a followup PR that pulls in the necessary delete-path mocking
    // infrastructure (likely a shared FakeGatewaySwitch in app-common-test).
}
