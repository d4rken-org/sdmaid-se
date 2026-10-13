package eu.darken.sdmse.appcleaner.core.automation.specs.aosp

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class ManageSpaceDetourTest : BaseTest() {

    @Test
    fun `detour when CENTER was sent and the target app owns the window`() {
        isManageSpaceDetour(centerSent = true, windowPkg = VANADIUM, targetPkg = VANADIUM) shouldBe true
    }

    @Test
    fun `no detour without a sent CENTER`() {
        isManageSpaceDetour(centerSent = false, windowPkg = VANADIUM, targetPkg = VANADIUM) shouldBe false
    }

    @Test
    fun `no detour without a window package`() {
        isManageSpaceDetour(centerSent = true, windowPkg = null, targetPkg = VANADIUM) shouldBe false
    }

    @Test
    fun `no detour when another package owns the window`() {
        isManageSpaceDetour(centerSent = true, windowPkg = SETTINGS, targetPkg = VANADIUM) shouldBe false
    }

    @Test
    fun `no detour when Settings is the target`() {
        isManageSpaceDetour(centerSent = true, windowPkg = SETTINGS, targetPkg = SETTINGS) shouldBe false
    }

    @Test
    fun `three-button row predicted when all conditions hold`() {
        predictsManageStorageButton(
            settingsHasManageStorageText = true,
            manageSpaceActivityName = MANAGE_SPACE_ACTIVITY,
            manageSpaceActivityResolves = true,
        ) shouldBe true
    }

    @Test
    fun `no three-button row when Settings lacks the resource`() {
        predictsManageStorageButton(
            settingsHasManageStorageText = false,
            manageSpaceActivityName = MANAGE_SPACE_ACTIVITY,
            manageSpaceActivityResolves = true,
        ) shouldBe false
    }

    @Test
    fun `no three-button row without a manageSpaceActivity`() {
        predictsManageStorageButton(
            settingsHasManageStorageText = true,
            manageSpaceActivityName = null,
            manageSpaceActivityResolves = true,
        ) shouldBe false
    }

    @Test
    fun `no three-button row when the manageSpaceActivity does not resolve`() {
        predictsManageStorageButton(
            settingsHasManageStorageText = true,
            manageSpaceActivityName = MANAGE_SPACE_ACTIVITY,
            manageSpaceActivityResolves = false,
        ) shouldBe false
    }

    private val original = snapshot(12_000_000L to "12 MB", 3_000_000L to "3 MB")
    private val settled = snapshot(12_000_000L to "12 MB", 3_100_000L to "3.1 MB")
    private val calculating = snapshot(12_000_000L to "12 MB", null to "Calculating…")

    private class Fake(
        var back: Boolean = true,
        var anchor: Boolean = true,
        var snapshots: List<StorageSnapshot>,
        var bootstrap: Boolean = true,
        var down: Boolean = true,
        var right: Boolean = true,
        var left: Boolean = true,
        var veto: Boolean = false,
        var center: Boolean = true,
        var validate: Boolean = true,
    ) {
        val calls = mutableListOf<String>()
        var validatedBaseline: StorageSnapshot? = null
        private var snapshotIdx = 0

        val ops = ManageSpaceDetourOps(
            back = { calls += BACK; back },
            awaitAnchor = { calls += ANCHOR; anchor },
            takeSnapshot = {
                calls += SNAPSHOT
                snapshots[minOf(snapshotIdx++, snapshots.lastIndex)]
            },
            bootstrapAnchor = { calls += BOOTSTRAP; bootstrap },
            dpadDown = { calls += DOWN; down },
            dpadRight = { calls += RIGHT; right },
            dpadLeft = { calls += LEFT; left },
            focusVetoesCenter = { calls += GUARD; veto },
            dpadCenter = { calls += CENTER; center },
            validate = { baseline ->
                calls += VALIDATE
                validatedBaseline = baseline
                validate
            },
        )
    }

    private suspend fun Fake.recover(): Boolean = recoverFromManageSpaceDetour(
        tag = "test",
        ops = ops,
        originalSnapshot = original,
        stepDelayMs = 120,
    )

    @Test
    fun `success path runs every step once in order and validates against the settled baseline`() = runTest {
        val fake = Fake(snapshots = listOf(settled))

        fake.recover() shouldBe true

        fake.calls shouldContainExactly listOf(
            BACK, ANCHOR, SNAPSHOT, BOOTSTRAP, DOWN, RIGHT, LEFT, GUARD, CENTER, VALIDATE,
        )
        fake.validatedBaseline shouldBeSameInstanceAs settled
    }

    @Test
    fun `snapshot settling on a later poll is the baseline`() = runTest {
        val fake = Fake(snapshots = listOf(calculating, snapshot(12_000_000L to "12 MB"), settled))

        fake.recover() shouldBe true

        fake.calls shouldContainExactly listOf(
            BACK, ANCHOR, SNAPSHOT, SNAPSHOT, SNAPSHOT, BOOTSTRAP, DOWN, RIGHT, LEFT, GUARD, CENTER, VALIDATE,
        )
        fake.validatedBaseline shouldBeSameInstanceAs settled
    }

    @Test
    fun `failed BACK stops before anything else`() = runTest {
        val fake = Fake(back = false, snapshots = listOf(settled))

        fake.recover() shouldBe false

        fake.calls shouldContainExactly listOf(BACK)
    }

    @Test
    fun `anchor not restored stops before any key`() = runTest {
        val fake = Fake(anchor = false, snapshots = listOf(settled))

        fake.recover() shouldBe false

        fake.calls shouldContainExactly listOf(BACK, ANCHOR)
    }

    @Test
    fun `snapshot that never settles stops before any key`() = runTest {
        val fake = Fake(snapshots = listOf(calculating))

        fake.recover() shouldBe false

        fake.calls shouldContainExactly listOf(BACK, ANCHOR) + List(10) { SNAPSHOT }
    }

    @Test
    fun `failed bootstrap stops before any key`() = runTest {
        val fake = Fake(bootstrap = false, snapshots = listOf(settled))

        fake.recover() shouldBe false

        fake.calls shouldContainExactly listOf(BACK, ANCHOR, SNAPSHOT, BOOTSTRAP)
    }

    @Test
    fun `failed DOWN sends no CENTER`() = runTest {
        val fake = Fake(down = false, snapshots = listOf(settled))

        fake.recover() shouldBe false

        fake.calls shouldContainExactly listOf(BACK, ANCHOR, SNAPSHOT, BOOTSTRAP, DOWN)
    }

    @Test
    fun `failed RIGHT sends no CENTER`() = runTest {
        val fake = Fake(right = false, snapshots = listOf(settled))

        fake.recover() shouldBe false

        fake.calls shouldContainExactly listOf(BACK, ANCHOR, SNAPSHOT, BOOTSTRAP, DOWN, RIGHT)
    }

    @Test
    fun `failed LEFT sends no CENTER`() = runTest {
        val fake = Fake(left = false, snapshots = listOf(settled))

        fake.recover() shouldBe false

        fake.calls shouldContainExactly listOf(BACK, ANCHOR, SNAPSHOT, BOOTSTRAP, DOWN, RIGHT, LEFT)
    }

    @Test
    fun `guard veto sends no CENTER`() = runTest {
        val fake = Fake(veto = true, snapshots = listOf(settled))

        fake.recover() shouldBe false

        fake.calls shouldContainExactly listOf(BACK, ANCHOR, SNAPSHOT, BOOTSTRAP, DOWN, RIGHT, LEFT, GUARD)
    }

    @Test
    fun `failed CENTER skips validation`() = runTest {
        val fake = Fake(center = false, snapshots = listOf(settled))

        fake.recover() shouldBe false

        fake.calls shouldContainExactly listOf(BACK, ANCHOR, SNAPSHOT, BOOTSTRAP, DOWN, RIGHT, LEFT, GUARD, CENTER)
    }

    @Test
    fun `failed validation is the result and nothing follows`() = runTest {
        val fake = Fake(validate = false, snapshots = listOf(settled))

        fake.recover() shouldBe false

        fake.calls shouldContainExactly listOf(
            BACK, ANCHOR, SNAPSHOT, BOOTSTRAP, DOWN, RIGHT, LEFT, GUARD, CENTER, VALIDATE,
        )
    }

    @Test
    fun `row that was unparseable before may stay unparseable`() {
        val pre = snapshot(12_000_000L to "12 MB", null to "0 o")

        snapshot(12_000_000L to "12 MB", null to "0 o").isSettledAgainst(pre) shouldBe true
    }

    @Test
    fun `different row count is not settled`() {
        snapshot(12_000_000L to "12 MB").isSettledAgainst(original) shouldBe false
    }

    @Test
    fun `parseable row turned unparseable is not settled`() {
        calculating.isSettledAgainst(original) shouldBe false
    }

    companion object {
        private const val VANADIUM = "app.vanadium.browser"
        private const val SETTINGS = "com.android.settings"
        private const val MANAGE_SPACE_ACTIVITY = "org.chromium.chrome.browser.site_settings.ManageSpaceActivity"

        private const val BACK = "BACK"
        private const val ANCHOR = "ANCHOR"
        private const val SNAPSHOT = "SNAPSHOT"
        private const val BOOTSTRAP = "BOOTSTRAP"
        private const val DOWN = "DOWN"
        private const val RIGHT = "RIGHT"
        private const val LEFT = "LEFT"
        private const val GUARD = "GUARD"
        private const val CENTER = "CENTER"
        private const val VALIDATE = "VALIDATE"

        private fun snapshot(vararg values: Pair<Long?, String>) = StorageSnapshot(
            values.map { (bytes, raw) -> StorageSnapshot.ParsedSize(bytes, raw) }
        )
    }
}
