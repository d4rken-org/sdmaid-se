package eu.darken.sdmse.appcontrol.ui.list

import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.appcontrol.core.SortSettings
import eu.darken.sdmse.common.compose.FastScrollSection
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class AppControlFastScrollerSectionsTest : BaseTest() {

    private fun row(name: String, pkg: String): AppControlListViewModel.Row = AppControlListViewModel.Row(
        appInfo = mockk<AppInfo>(relaxed = true),
        sectionKeyName = name,
        sectionKeyPkg = pkg,
    )

    @Test
    fun `NAME sort produces deduplicated alpha sections in row order`() {
        val rows = listOf(
            row(name = "A", pkg = "C"),
            row(name = "A", pkg = "B"),
            row(name = "B", pkg = "A"),
            row(name = "C", pkg = "A"),
            row(name = "C", pkg = "A"),
        )
        val sections = buildFastScrollerSections(rows, SortSettings.Mode.NAME)
        sections shouldBe listOf(
            FastScrollSection(itemIndex = 0, label = "A"),
            FastScrollSection(itemIndex = 2, label = "B"),
            FastScrollSection(itemIndex = 3, label = "C"),
        )
    }

    @Test
    fun `PACKAGENAME sort produces sections from sectionKeyPkg`() {
        val rows = listOf(
            row(name = "Z", pkg = "C"),
            row(name = "Z", pkg = "B"),
            row(name = "Z", pkg = "A"),
        )
        val sections = buildFastScrollerSections(rows, SortSettings.Mode.PACKAGENAME)
        sections shouldBe listOf(
            FastScrollSection(itemIndex = 0, label = "C"),
            FastScrollSection(itemIndex = 1, label = "B"),
            FastScrollSection(itemIndex = 2, label = "A"),
        )
    }

    @Test
    fun `SIZE sort yields no sections in v1`() {
        val rows = listOf(row(name = "A", pkg = "A"), row(name = "B", pkg = "B"))
        buildFastScrollerSections(rows, SortSettings.Mode.SIZE) shouldBe emptyList()
    }

    @Test
    fun `LAST_UPDATE sort yields no sections in v1`() {
        val rows = listOf(row(name = "A", pkg = "A"))
        buildFastScrollerSections(rows, SortSettings.Mode.LAST_UPDATE) shouldBe emptyList()
    }

    @Test
    fun `INSTALLED_AT sort yields no sections in v1`() {
        val rows = listOf(row(name = "A", pkg = "A"))
        buildFastScrollerSections(rows, SortSettings.Mode.INSTALLED_AT) shouldBe emptyList()
    }

    @Test
    fun `SCREEN_TIME sort yields no sections in v1`() {
        val rows = listOf(row(name = "A", pkg = "A"))
        buildFastScrollerSections(rows, SortSettings.Mode.SCREEN_TIME) shouldBe emptyList()
    }

    @Test
    fun `empty input yields empty sections`() {
        buildFastScrollerSections(emptyList(), SortSettings.Mode.NAME) shouldBe emptyList()
    }

    @Test
    fun `consecutive duplicates are collapsed`() {
        val rows = List(10) { row(name = "A", pkg = "A") }
        buildFastScrollerSections(rows, SortSettings.Mode.NAME) shouldBe listOf(
            FastScrollSection(itemIndex = 0, label = "A"),
        )
    }
}
