package eu.darken.sdmse.main.core

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

class NavigationIdConsistencyTest : BaseTest() {

    private val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()

    @Test
    fun `all tool module action IDs must exist in main_nav xml`() {
        val projectRoot = File("..")

        val navGraphActions = parseNavGraphActionIds(
            File(projectRoot, "app/src/main/res/navigation/main_nav.xml")
        )
        navGraphActions.shouldNotBeEmpty()

        val toolModuleIds = projectRoot.listFiles { f -> f.isDirectory && f.name.startsWith("app-tool-") }
            ?.flatMap { moduleDir ->
                val idsFile = File(moduleDir, "src/main/res/values/ids.xml")
                if (!idsFile.exists()) return@flatMap emptyList()
                parseIdsXmlActionIds(idsFile).map { actionName -> moduleDir.name to actionName }
            }
            ?: emptyList()

        toolModuleIds.shouldNotBeEmpty()

        val mismatches = toolModuleIds.filter { (_, actionName) -> actionName !in navGraphActions }
        mismatches shouldBe emptyList()
    }

    private fun parseNavGraphActionIds(navFile: File): Set<String> {
        val doc = docBuilder.parse(navFile)
        val actions = doc.getElementsByTagName("action")
        return (0 until actions.length).mapNotNull { i ->
            val id = (actions.item(i) as Element).getAttribute("android:id")
            id.removePrefix("@+id/").removePrefix("@id/").takeIf { it.startsWith("action_") }
        }.toSet()
    }

    private fun parseIdsXmlActionIds(idsFile: File): List<String> {
        val doc = docBuilder.parse(idsFile)
        val items = doc.getElementsByTagName("item")
        return (0 until items.length).mapNotNull { i ->
            val element = items.item(i) as Element
            val name = element.getAttribute("name")
            val type = element.getAttribute("type")
            name.takeIf { type == "id" && it.startsWith("action_") }
        }
    }
}
