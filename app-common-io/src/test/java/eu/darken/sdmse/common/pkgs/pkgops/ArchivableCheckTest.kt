package eu.darken.sdmse.common.pkgs.pkgops

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class ArchivableCheckTest : BaseTest() {

    @Test
    fun `responsible installer prefers a non-empty update owner`() {
        ArchivableCheck.responsibleInstaller("eu.thlab.owner", "eu.thlab.installer") shouldBe "eu.thlab.owner"
    }

    @Test
    fun `responsible installer falls back to the installing package`() {
        ArchivableCheck.responsibleInstaller(null, "eu.thlab.installer") shouldBe "eu.thlab.installer"
        ArchivableCheck.responsibleInstaller("", "eu.thlab.installer") shouldBe "eu.thlab.installer"
    }

    @Test
    fun `responsible installer is null without any installer`() {
        ArchivableCheck.responsibleInstaller(null, null) shouldBe null
        ArchivableCheck.responsibleInstaller("", "") shouldBe null
        ArchivableCheck.responsibleInstaller(null, "") shouldBe null
    }

    private fun check(
        installerOf: (String, Int) -> String? = { _, _ -> "eu.thlab.installer" },
        hasUnarchiveReceiver: (String, Int) -> Boolean = { _, _ -> true },
        hasLauncherActivity: (String, Int) -> Boolean = { _, _ -> true },
        isOptedOut: (String) -> Boolean = { false },
    ) = ArchivableCheck.isArchivable(
        packageName = "eu.thlab.target",
        userId = 10,
        installerOf = installerOf,
        hasUnarchiveReceiver = hasUnarchiveReceiver,
        hasLauncherActivity = hasLauncherActivity,
        isOptedOut = isOptedOut,
    )

    @Test
    fun `archivable when every check passes`() {
        check() shouldBe true
    }

    @Test
    fun `not archivable without an installer`() {
        check(installerOf = { _, _ -> null }) shouldBe false
    }

    @Test
    fun `not archivable when the installer has no unarchive receiver`() {
        check(hasUnarchiveReceiver = { _, _ -> false }) shouldBe false
    }

    @Test
    fun `not archivable without a launcher activity`() {
        check(hasLauncherActivity = { _, _ -> false }) shouldBe false
    }

    @Test
    fun `not archivable when opted out`() {
        check(isOptedOut = { true }) shouldBe false
    }

    @Test
    fun `not archivable when any lookup throws`() {
        check(installerOf = { _, _ -> throw IllegalStateException() }) shouldBe false
        check(hasUnarchiveReceiver = { _, _ -> throw IllegalStateException() }) shouldBe false
        check(hasLauncherActivity = { _, _ -> throw IllegalStateException() }) shouldBe false
        check(isOptedOut = { throw IllegalStateException() }) shouldBe false
    }

    @Test
    fun `lookups target the package and user, the receiver lookup the resolved installer`() {
        val installerCalls = mutableListOf<Pair<String, Int>>()
        val receiverCalls = mutableListOf<Pair<String, Int>>()
        val launcherCalls = mutableListOf<Pair<String, Int>>()
        val optOutCalls = mutableListOf<String>()

        check(
            installerOf = { pkg, user -> "eu.thlab.store".also { installerCalls += pkg to user } },
            hasUnarchiveReceiver = { installer, user -> true.also { receiverCalls += installer to user } },
            hasLauncherActivity = { pkg, user -> true.also { launcherCalls += pkg to user } },
            isOptedOut = { pkg -> false.also { optOutCalls += pkg } },
        ) shouldBe true

        installerCalls shouldBe listOf("eu.thlab.target" to 10)
        receiverCalls shouldBe listOf("eu.thlab.store" to 10)
        launcherCalls shouldBe listOf("eu.thlab.target" to 10)
        optOutCalls shouldBe listOf("eu.thlab.target")
    }
}
