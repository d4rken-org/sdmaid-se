package eu.darken.sdmse.common.pkgs

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class PkgInventoryCheckTest : BaseTest() {

    private val ownPkg = "eu.darken.sdmse"

    @Test
    fun `empty list is rejected`() {
        PkgInventoryCheck.check(emptyList(), ownPkg) shouldBe PkgInventoryCheck.Result.Empty
    }

    @Test
    fun `list missing the caller is rejected`() {
        PkgInventoryCheck.check(listOf("android", "com.android.cts.ctsshim"), ownPkg) shouldBe
            PkgInventoryCheck.Result.MissingOwn
    }

    @Test
    fun `Huawei TAF-sandboxed list with only caller is rejected as MissingCore`() {
        PkgInventoryCheck.check(listOf(ownPkg, "com.huawei.systemmanager"), ownPkg) shouldBe
            PkgInventoryCheck.Result.MissingCore
    }

    @Test
    fun `list with caller plus android sanity pkg is valid`() {
        PkgInventoryCheck.check(listOf(ownPkg, "android", "com.huawei.foo"), ownPkg) shouldBe
            PkgInventoryCheck.Result.Valid
    }

    @Test
    fun `list with caller plus ctsshim sanity pkg is valid`() {
        PkgInventoryCheck.check(listOf(ownPkg, "com.android.cts.ctsshim"), ownPkg) shouldBe
            PkgInventoryCheck.Result.Valid
    }

    @Test
    fun `caller plus single core anchor is intentionally treated as valid`() {
        // Documents the deliberately permissive heuristic: a single core anchor (android or
        // ctsshim) is enough. Tightening this risks false positives on minimal AOSP/test images.
        // See review notes on issue 2419.
        PkgInventoryCheck.check(listOf(ownPkg, "android"), ownPkg) shouldBe PkgInventoryCheck.Result.Valid
    }
}
