package eu.darken.sdmse.common.coil

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.test.core.app.ApplicationProvider
import coil.ImageLoader
import coil.decode.DataSource
import coil.request.ImageRequest
import coil.request.Options
import coil.request.SuccessResult
import coil.size.Size
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.container.NormalPkg
import eu.darken.sdmse.common.pkgs.features.InstallerInfo
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import testhelpers.coroutine.TestDispatcherProvider
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class AppIconCacheTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val keyer = AppIconKeyer()

    @Test
    fun `equivalent app metadata produces the same key`() {
        val first = installedPkg(user = 10, version = 42, updated = 1234, iconResource = 7)
        val second = installedPkg(user = 10, version = 42, updated = 1234, iconResource = 7)

        key(first, size = 96) shouldBe key(second, size = 96)
    }

    @Test
    fun `user app update icon and requested size invalidate the key`() {
        val base = installedPkg(user = 0, version = 1, updated = 100, iconResource = 7)
        val baseKey = key(base, size = 96)

        key(installedPkg(user = 10, version = 1, updated = 100, iconResource = 7), 96) shouldNotBe baseKey
        key(installedPkg(user = 0, version = 2, updated = 100, iconResource = 7), 96) shouldNotBe baseKey
        key(installedPkg(user = 0, version = 1, updated = 200, iconResource = 7), 96) shouldNotBe baseKey
        key(installedPkg(user = 0, version = 1, updated = 100, iconResource = 8), 96) shouldNotBe baseKey
        key(base, size = 128) shouldNotBe baseKey
    }

    @Test
    fun `second image request uses the global memory cache`() {
        runBlocking {
            val fetchCount = AtomicInteger()
            val pkg = CountingPkg(Pkg.Id("test.cached.icon"), fetchCount)
            val ipcFunnel = IPCFunnel(context, TestDispatcherProvider())
            val imageLoader = ImageLoader.Builder(context)
                .diskCache(null)
                .dispatcher(Dispatchers.Unconfined)
                .interceptorDispatcher(Dispatchers.Unconfined)
                .components {
                    add(AppIconKeyer())
                    add(AppIconFetcher.Factory(ipcFunnel))
                }
                .build()

            try {
                fun request() = ImageRequest.Builder(context)
                    .data(pkg)
                    .size(64)
                    .allowHardware(false)
                    .build()

                val first = imageLoader.execute(request()) as SuccessResult
                val second = imageLoader.execute(request()) as SuccessResult

                first.dataSource shouldBe DataSource.MEMORY
                (first.drawable is BitmapDrawable) shouldBe true
                (first.drawable as BitmapDrawable).bitmap.run {
                    width shouldBe 64
                    height shouldBe 64
                }
                second.dataSource shouldBe DataSource.MEMORY_CACHE
                fetchCount.get() shouldBe 1
            } finally {
                imageLoader.shutdown()
            }
        }
    }

    private fun key(pkg: Pkg, size: Int): String = keyer.key(
        pkg,
        Options(context = context, size = Size(size, size)),
    )

    private fun installedPkg(
        user: Int,
        version: Long,
        updated: Long,
        iconResource: Int,
    ) = NormalPkg(
        packageInfo = PackageInfo().apply {
            packageName = "test.installed.icon"
            longVersionCode = version
            lastUpdateTime = updated
            applicationInfo = ApplicationInfo().apply {
                icon = iconResource
                sourceDir = "/data/app/test.installed.icon/base.apk"
            }
        },
        installerInfo = InstallerInfo(),
        userHandle = UserHandle2(user),
    )

    private class CountingPkg(
        override val id: Pkg.Id,
        fetchCount: AtomicInteger,
    ) : Pkg {
        override val label: CaString? = null
        override val icon: ((Context) -> Drawable) = {
            fetchCount.incrementAndGet()
            ColorDrawable(Color.RED)
        }
    }
}
