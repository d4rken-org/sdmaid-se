package eu.darken.sdmse.common.pkgs.features

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.drawable.Drawable
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class SourceAvailableTest : BaseTest() {

    private class TestPkg(override val packageInfo: PackageInfo) : SourceAvailable {
        override val userHandle: UserHandle2 = UserHandle2(0)
        override val label: CaString? = null
        override val icon: ((Context) -> Drawable)? = null
    }

    private fun pkg(
        sourceDir: String? = "/data/app/test.pkg/base.apk",
        splitNames: Array<String?>? = null,
        splitSourceDirs: Array<String?>? = null,
    ) = TestPkg(
        PackageInfo().apply {
            packageName = "test.pkg"
            applicationInfo = ApplicationInfo().apply {
                this.packageName = "test.pkg"
                this.sourceDir = sourceDir
                this.splitNames = splitNames
                this.splitSourceDirs = splitSourceDirs
            }
        }
    )

    @Test
    fun `splits are paired with their names by index`() {
        val target = pkg(
            splitNames = arrayOf("config.arm64_v8a", "config.xxhdpi"),
            splitSourceDirs = arrayOf(
                "/data/app/test.pkg/split_config.arm64_v8a.apk",
                "/data/app/test.pkg/split_config.xxhdpi.apk",
            ),
        )

        target.splitSourcesNamed!! shouldContainExactly listOf(
            SourceAvailable.SplitSource(
                id = "config.arm64_v8a",
                path = LocalPath.build("/data/app/test.pkg/split_config.arm64_v8a.apk"),
            ),
            SourceAvailable.SplitSource(
                id = "config.xxhdpi",
                path = LocalPath.build("/data/app/test.pkg/split_config.xxhdpi.apk"),
            ),
        )
        // The unordered view stays what it was, both of its callers rely on it.
        target.splitSources shouldBe setOf(
            LocalPath.build("/data/app/test.pkg/split_config.arm64_v8a.apk"),
            LocalPath.build("/data/app/test.pkg/split_config.xxhdpi.apk"),
        )
    }

    @Test
    fun `an app without splits has no named splits`() {
        pkg().splitSourcesNamed shouldBe null
    }

    @Test
    fun `missing split names are a failure, not an empty result`() {
        val target = pkg(
            splitNames = null,
            splitSourceDirs = arrayOf("/data/app/test.pkg/split_config.xxhdpi.apk"),
        )

        target.splitSourcesNamed shouldBe null
        target.splitSources!!.size shouldBe 1
    }

    @Test
    fun `differing lengths are a failure instead of a truncated pairing`() {
        val target = pkg(
            splitNames = arrayOf("config.arm64_v8a"),
            splitSourceDirs = arrayOf(
                "/data/app/test.pkg/split_config.arm64_v8a.apk",
                "/data/app/test.pkg/split_config.xxhdpi.apk",
            ),
        )

        target.splitSourcesNamed shouldBe null
    }

    @Test
    fun `a null entry is a failure`() {
        val target = pkg(
            splitNames = arrayOf("config.arm64_v8a", null),
            splitSourceDirs = arrayOf(
                "/data/app/test.pkg/split_config.arm64_v8a.apk",
                "/data/app/test.pkg/split_config.xxhdpi.apk",
            ),
        )

        target.splitSourcesNamed shouldBe null
    }
}
