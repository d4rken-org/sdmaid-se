package eu.darken.sdmse.appcontrol.ui.list.actions.items

import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.appcontrol.core.usage.UsageInfo
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallDetails
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class AppActionItemBuilderTest : BaseTest() {

    private val pkgId = Pkg.Id("com.test.app")
    private val installId = InstallId(pkgId, UserHandle2(0))
    private val sizes = PkgOps.SizeStats(
        appBytes = 100,
        cacheBytes = 10,
        externalCacheBytes = 0,
        dataBytes = 50,
    )

    private fun installed(enabled: Boolean = true): Installed {
        // Real packages implement both Installed and InstallDetails. The Pkg.isEnabled extension
        // checks `this is InstallDetails && this.isEnabled`, so the mock needs both interfaces.
        val mock = mockk<Installed>(relaxed = true, moreInterfaces = arrayOf(InstallDetails::class))
        every { (mock as InstallDetails).isEnabled } returns enabled
        return mock
    }

    private fun appInfo(
        canBeStopped: Boolean = false,
        canBeExported: Boolean = false,
        canBeDeleted: Boolean = false,
        canBeArchived: Boolean = false,
        canBeRestored: Boolean = false,
        canBeToggled: Boolean = false,
        sizes: PkgOps.SizeStats? = null,
        usage: UsageInfo? = null,
        enabled: Boolean = true,
    ): AppInfo = AppInfo(
        pkg = installed(enabled = enabled),
        isActive = null,
        sizes = sizes,
        usage = usage,
        userProfile = null,
        canBeToggled = canBeToggled,
        canBeStopped = canBeStopped,
        canBeExported = canBeExported,
        canBeDeleted = canBeDeleted,
        canBeArchived = canBeArchived,
        canBeRestored = canBeRestored,
    ).also { info ->
        // installId is computed from pkg.installId; mockk relaxed pkg returns its own InstallId.
        // We need installId to match `installId`, so override the pkg.installId path.
        val pkg = info.pkg
        every { pkg.installId } returns installId
        every { pkg.id } returns pkgId
    }

    private val ctxAllAvailable = AppActionItemContext(
        isCurrentUser = true,
        launchAvailable = true,
        appStoreAvailable = true,
        canForceStop = true,
        canArchive = true,
        canRestore = true,
        canToggle = true,
        existingExclusionId = null,
    )

    @Test
    fun `current-user app with all caps emits info rows then full action set in fixed order`() {
        val info = appInfo(
            canBeStopped = true,
            canBeExported = true,
            canBeDeleted = true,
            canBeArchived = true,
            canBeRestored = true,
            canBeToggled = true,
            sizes = sizes,
        )

        val items = buildAppActionItems(info, ctxAllAvailable)

        items.map { it::class }.shouldContainExactly(
            AppActionItem.Info.Size::class,
            AppActionItem.Info.Usage::class,
            AppActionItem.Action.Launch::class,
            AppActionItem.Action.ForceStop::class,
            AppActionItem.Action.SystemSettings::class,
            AppActionItem.Action.AppStore::class,
            AppActionItem.Action.Exclude::class,
            AppActionItem.Action.Toggle::class,
            AppActionItem.Action.Uninstall::class,
            AppActionItem.Action.Archive::class,
            AppActionItem.Action.Restore::class,
            AppActionItem.Action.Export::class,
        )
    }

    @Test
    fun `cross-user app drops Launch SystemSettings AppStore Exclude Export`() {
        val info = appInfo(
            canBeStopped = true,
            canBeExported = true,
            canBeDeleted = true,
            canBeArchived = true,
            canBeRestored = true,
            canBeToggled = true,
            sizes = sizes,
        )

        val items = buildAppActionItems(info, ctxAllAvailable.copy(isCurrentUser = false))

        items.map { it::class }.shouldContainExactly(
            AppActionItem.Info.Size::class,
            AppActionItem.Info.Usage::class,
            AppActionItem.Action.ForceStop::class,
            AppActionItem.Action.Toggle::class,
            AppActionItem.Action.Uninstall::class,
            AppActionItem.Action.Archive::class,
            AppActionItem.Action.Restore::class,
        )
    }

    @Test
    fun `existing exclusion is carried on the Exclude item`() {
        val info = appInfo(canBeDeleted = true)

        val items = buildAppActionItems(
            info,
            ctxAllAvailable.copy(existingExclusionId = "exclusion-id-42"),
        )

        val exclude = items.filterIsInstance<AppActionItem.Action.Exclude>().single()
        exclude.existingExclusionId shouldBe "exclusion-id-42"
    }

    @Test
    fun `disabled app emits Toggle with isEnabled false`() {
        val info = appInfo(canBeToggled = true, enabled = false)

        val items = buildAppActionItems(info, ctxAllAvailable)

        val toggle = items.filterIsInstance<AppActionItem.Action.Toggle>().single()
        toggle.isEnabled shouldBe false
    }

    @Test
    fun `enabled app emits Toggle with isEnabled true`() {
        val info = appInfo(canBeToggled = true, enabled = true)

        val items = buildAppActionItems(info, ctxAllAvailable)

        val toggle = items.filterIsInstance<AppActionItem.Action.Toggle>().single()
        toggle.isEnabled shouldBe true
    }

    @Test
    fun `archive support flag controls Archive visibility`() {
        val info = appInfo(canBeArchived = true)

        val withArchive = buildAppActionItems(info, ctxAllAvailable)
        withArchive.any { it is AppActionItem.Action.Archive } shouldBe true

        val withoutArchive = buildAppActionItems(info, ctxAllAvailable.copy(canArchive = false))
        withoutArchive.any { it is AppActionItem.Action.Archive } shouldBe false
    }

    @Test
    fun `restore support flag controls Restore visibility`() {
        val info = appInfo(canBeRestored = true)

        val withRestore = buildAppActionItems(info, ctxAllAvailable)
        withRestore.any { it is AppActionItem.Action.Restore } shouldBe true

        val withoutRestore = buildAppActionItems(info, ctxAllAvailable.copy(canRestore = false))
        withoutRestore.any { it is AppActionItem.Action.Restore } shouldBe false
    }

    @Test
    fun `Size info row only emitted when sizes available`() {
        val withSizes = buildAppActionItems(appInfo(sizes = sizes), ctxAllAvailable)
        withSizes.first().shouldBeInstanceOf<AppActionItem.Info.Size>()

        val withoutSizes = buildAppActionItems(appInfo(sizes = null), ctxAllAvailable)
        withoutSizes.any { it is AppActionItem.Info.Size } shouldBe false
        // Usage info row always emits regardless.
        withoutSizes.first().shouldBeInstanceOf<AppActionItem.Info.Usage>()
    }

    @Test
    fun `Usage info row carries installedAt and updatedAt and usage when present`() {
        val now = Instant.ofEpochSecond(1_700_000_000)
        val pkgMock = mockk<Installed>(
            relaxed = true,
            moreInterfaces = arrayOf(InstallDetails::class),
        )
        every { (pkgMock as InstallDetails).isEnabled } returns true
        every { (pkgMock as InstallDetails).installedAt } returns now
        every { (pkgMock as InstallDetails).updatedAt } returns now.plusSeconds(60)
        every { pkgMock.installId } returns installId
        val info = AppInfo(
            pkg = pkgMock,
            isActive = null,
            sizes = null,
            usage = null,
            userProfile = null,
            canBeToggled = false,
            canBeStopped = false,
            canBeExported = false,
            canBeDeleted = false,
            canBeArchived = false,
            canBeRestored = false,
        )

        val items = buildAppActionItems(info, ctxAllAvailable)

        val usageItem = items.filterIsInstance<AppActionItem.Info.Usage>().single()
        usageItem.installedAt shouldBe now
        usageItem.updatedAt shouldBe now.plusSeconds(60)
        usageItem.usage shouldBe null
    }

    @Test
    fun `Export only emitted when canBeExported and current user`() {
        val info = appInfo(canBeExported = true)

        val asCurrent = buildAppActionItems(info, ctxAllAvailable)
        asCurrent.any { it is AppActionItem.Action.Export } shouldBe true

        val asOther = buildAppActionItems(info, ctxAllAvailable.copy(isCurrentUser = false))
        asOther.any { it is AppActionItem.Action.Export } shouldBe false

        val notExportable = buildAppActionItems(appInfo(canBeExported = false), ctxAllAvailable)
        notExportable.any { it is AppActionItem.Action.Export } shouldBe false
    }

    @Test
    fun `Launch hidden when launchAvailable is false`() {
        val info = appInfo()
        val items = buildAppActionItems(info, ctxAllAvailable.copy(launchAvailable = false))
        items.any { it is AppActionItem.Action.Launch } shouldBe false
    }

    @Test
    fun `AppStore hidden when appStoreAvailable is false`() {
        val info = appInfo()
        val items = buildAppActionItems(info, ctxAllAvailable.copy(appStoreAvailable = false))
        items.any { it is AppActionItem.Action.AppStore } shouldBe false
    }
}
