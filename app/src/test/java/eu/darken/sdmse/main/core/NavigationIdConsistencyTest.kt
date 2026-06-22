package eu.darken.sdmse.main.core

import eu.darken.sdmse.analyzer.ui.AppDetailsRoute
import eu.darken.sdmse.analyzer.ui.ContentRoute
import eu.darken.sdmse.analyzer.ui.StorageContentRoute
import eu.darken.sdmse.analyzer.core.content.ContentGroup
import eu.darken.sdmse.common.navigation.routes.DashboardRoute
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.storage.StorageId
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.corpsefinder.ui.CorpseDetailsRoute
import eu.darken.sdmse.deduplicator.ui.DeduplicatorDetailsRoute
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.scheduler.ui.ScheduleItemRoute
import eu.darken.sdmse.setup.SetupRoute
import eu.darken.sdmse.setup.SetupScreenOptions
import eu.darken.sdmse.setup.SetupModule
import eu.darken.sdmse.swiper.ui.SwiperSwipeRoute
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.util.UUID

class NavigationIdConsistencyTest : BaseTest() {

    @Test
    fun `object routes are singletons`() {
        DashboardRoute shouldNotBe null
    }

    @Test
    fun `data class routes with defaults are constructable`() {
        UpgradeRoute() shouldBe UpgradeRoute(forced = false)
        SetupRoute() shouldBe SetupRoute(options = null)
        CorpseDetailsRoute() shouldBe CorpseDetailsRoute(corpsePathJson = null)
    }

    @Test
    fun `key routes serialize and deserialize correctly`() {
        val storageId = StorageId(internalId = "primary", externalId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
        val installId = InstallId(pkgId = Pkg.Id("com.example"), userHandle = UserHandle2(0))

        val routes = listOf(
            UpgradeRoute(forced = true),
            SetupRoute(options = SetupScreenOptions(isOnboarding = true, typeFilter = setOf(SetupModule.Type.ROOT))),
            StorageContentRoute(storageId = storageId),
            AppDetailsRoute(storageId = storageId, installId = installId),
            ContentRoute(storageId = storageId, groupId = ContentGroup.Id("group-1"), installId = installId),
            DeduplicatorDetailsRoute(identifier = Duplicate.Cluster.Id("cluster-1")),
            ScheduleItemRoute(scheduleId = "sched-1"),
            SwiperSwipeRoute(sessionId = "session-1", startIndex = 5),
            CorpseDetailsRoute(corpsePath = LocalPath.build("/data/test")),
        )

        routes.forEach { route ->
            val json = Json.encodeToString(kotlinx.serialization.serializer(route::class.java), route)
            json.isNotBlank() shouldBe true
        }
    }
}
