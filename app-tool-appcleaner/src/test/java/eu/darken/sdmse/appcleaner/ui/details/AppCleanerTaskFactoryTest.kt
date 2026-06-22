package eu.darken.sdmse.appcleaner.ui.details

import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilterIdentifier
import eu.darken.sdmse.appcleaner.core.forensics.filter.AdvertisementFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.DefaultCachesPublicFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.ThumbnailsFilter
import eu.darken.sdmse.appcleaner.core.scanner.InaccessibleCache
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.pkgs.features.InstallId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class AppCleanerTaskFactoryTest : BaseTest() {

    private val installId = mockk<InstallId>(relaxed = true)

    private fun path(value: String): APath = mockk {
        every { this@mockk.path } returns value
        every { this@mockk.equals(any()) } answers {
            val other = it.invocation.args.firstOrNull() as? APath
            other?.path == value
        }
        every { this@mockk.hashCode() } returns value.hashCode()
    }

    private fun match(path: APath, gain: Long = 100): ExpendablesFilter.Match = mockk {
        every { this@mockk.path } returns path
        every { expectedGain } returns gain
    }

    private fun junk(
        expendables: Map<ExpendablesFilterIdentifier, Collection<ExpendablesFilter.Match>>? = null,
        inaccessibleCache: InaccessibleCache? = null,
    ): AppJunk = mockk {
        every { this@mockk.expendables } returns expendables
        every { this@mockk.inaccessibleCache } returns inaccessibleCache
    }

    @Test
    fun `WholeJunk produces task with includeInaccessible default true`() {
        val spec = DeleteSpec.WholeJunk(installId = installId, appLabel = "App")
        val task = buildAppCleanerTask(spec, junk())!!

        task.targetPkgs shouldBe setOf(installId)
        task.targetFilters shouldBe null
        task.targetContents shouldBe null
        task.includeInaccessible shouldBe true
        task.onlyInaccessible shouldBe false
    }

    @Test
    fun `Inaccessible without cache returns null`() {
        val spec = DeleteSpec.Inaccessible(installId = installId, appLabel = "App")
        buildAppCleanerTask(spec, junk(inaccessibleCache = null)) shouldBe null
    }

    @Test
    fun `Inaccessible with cache produces onlyInaccessible task`() {
        val cache = mockk<InaccessibleCache>(relaxed = true)
        val spec = DeleteSpec.Inaccessible(installId = installId, appLabel = "App")
        val task = buildAppCleanerTask(spec, junk(inaccessibleCache = cache))!!

        task.targetPkgs shouldBe setOf(installId)
        task.includeInaccessible shouldBe true
        task.onlyInaccessible shouldBe true
    }

    @Test
    fun `Category drops targetContents and forces includeInaccessible false`() {
        val a = path("/a")
        val b = path("/b")
        val spec = DeleteSpec.Category(
            installId = installId,
            category = ThumbnailsFilter::class,
            matchCount = 2,
            appLabel = "App",
            categoryLabel = "Thumbnails",
        )
        val task = buildAppCleanerTask(
            spec,
            junk(expendables = mapOf(ThumbnailsFilter::class to listOf(match(a), match(b)))),
        )!!

        task.targetPkgs shouldBe setOf(installId)
        task.targetFilters shouldBe setOf(ThumbnailsFilter::class)
        // Backend at AppCleaner.kt:226 uses `single { tc.matches(it.path) }` which can crash on
        // duplicate paths. Category deletes therefore omit targetContents — backend iterates the
        // category's matches directly.
        task.targetContents shouldBe null
        // Critical regression-prevention: a file/category delete must NOT also clear the app's
        // inaccessible cache, even though AppCleanerProcessingTask.includeInaccessible defaults
        // to true.
        task.includeInaccessible shouldBe false
        task.onlyInaccessible shouldBe false
    }

    @Test
    fun `Category against empty live matches returns null`() {
        val spec = DeleteSpec.Category(
            installId = installId,
            category = ThumbnailsFilter::class,
            matchCount = 0,
            appLabel = "App",
            categoryLabel = "Thumbnails",
        )
        buildAppCleanerTask(spec, junk(expendables = emptyMap())) shouldBe null
    }

    @Test
    fun `SingleFile produces single-path task with includeInaccessible false`() {
        val a = path("/a")
        val spec = DeleteSpec.SingleFile(
            installId = installId,
            category = ThumbnailsFilter::class,
            path = a,
            displayName = "/a",
        )
        val task = buildAppCleanerTask(
            spec,
            junk(expendables = mapOf(ThumbnailsFilter::class to listOf(match(a)))),
        )!!

        task.targetPkgs shouldBe setOf(installId)
        task.targetFilters shouldBe setOf(ThumbnailsFilter::class)
        task.targetContents shouldBe setOf(a)
        task.includeInaccessible shouldBe false
    }

    @Test
    fun `SingleFile whose path is no longer in the snapshot returns null`() {
        val a = path("/a")
        val spec = DeleteSpec.SingleFile(
            installId = installId,
            category = ThumbnailsFilter::class,
            path = a,
            displayName = "/a",
        )
        buildAppCleanerTask(spec, junk(expendables = mapOf(ThumbnailsFilter::class to emptyList()))) shouldBe null
    }

    @Test
    fun `SelectedFiles across two categories reconstructs targetFilters from snapshot`() {
        val a = path("/a")
        val b = path("/b")
        val spec = DeleteSpec.SelectedFiles(
            installId = installId,
            paths = setOf(a, b),
        )
        val task = buildAppCleanerTask(
            spec,
            junk(
                expendables = mapOf(
                    ThumbnailsFilter::class to listOf(match(a)),
                    AdvertisementFilter::class to listOf(match(b)),
                ),
            ),
        )!!

        task.targetPkgs shouldBe setOf(installId)
        task.targetFilters shouldBe setOf(ThumbnailsFilter::class, AdvertisementFilter::class)
        task.targetContents shouldBe setOf(a, b)
        task.includeInaccessible shouldBe false
    }

    @Test
    fun `SelectedFiles drops paths whose category vanished`() {
        val a = path("/a")
        val gone = path("/gone")
        val spec = DeleteSpec.SelectedFiles(
            installId = installId,
            paths = setOf(a, gone),
        )
        val task = buildAppCleanerTask(
            spec,
            junk(expendables = mapOf(DefaultCachesPublicFilter::class to listOf(match(a)))),
        )!!

        task.targetFilters shouldBe setOf(DefaultCachesPublicFilter::class)
        task.targetContents shouldBe setOf(a)
        task.includeInaccessible shouldBe false
    }

    @Test
    fun `SelectedFiles with all paths stale returns null`() {
        val gone = path("/gone")
        val spec = DeleteSpec.SelectedFiles(installId = installId, paths = setOf(gone))
        buildAppCleanerTask(spec, junk(expendables = emptyMap())) shouldBe null
    }

    @Test
    fun `non-Inaccessible specs all set includeInaccessible false`() {
        // Property-style assertion: spell out the regression-bait surface for a future reader.
        val a = path("/a")
        val expendables: Map<ExpendablesFilterIdentifier, Collection<ExpendablesFilter.Match>> =
            mapOf(ThumbnailsFilter::class to listOf(match(a)))
        val live = junk(expendables = expendables)

        val category = buildAppCleanerTask(
            DeleteSpec.Category(installId, ThumbnailsFilter::class, 1, "App", "Thumbnails"),
            live,
        )
        val singleFile = buildAppCleanerTask(
            DeleteSpec.SingleFile(installId, ThumbnailsFilter::class, a, "/a"),
            live,
        )
        val selected = buildAppCleanerTask(
            DeleteSpec.SelectedFiles(installId, setOf(a)),
            live,
        )

        category shouldNotBe null
        singleFile shouldNotBe null
        selected shouldNotBe null
        category!!.includeInaccessible shouldBe false
        singleFile!!.includeInaccessible shouldBe false
        selected!!.includeInaccessible shouldBe false
    }
}
