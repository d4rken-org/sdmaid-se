package eu.darken.sdmse.systemcleaner.core.filter.custom.backup

import eu.darken.sdmse.common.backup.RestoreMode
import eu.darken.sdmse.systemcleaner.core.filter.custom.CustomFilterConfig
import eu.darken.sdmse.systemcleaner.core.filter.custom.CustomFilterRepo
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class CustomFiltersBackupContributorTest : BaseTest() {

    private val repo = mockk<CustomFilterRepo>(relaxed = true)
    private val json = Json
    private val serializer = SetSerializer(CustomFilterConfig.serializer())
    private val contributor = CustomFiltersBackupContributor(repo, json)

    private fun config(id: String) = CustomFilterConfig(identifier = id, label = "Filter $id")

    @Test
    fun `merge saves restored filters without removing existing`() = runTest {
        val data = json.encodeToJsonElement(serializer, setOf(config("f1")))

        contributor.restore(data, RestoreMode.MERGE)

        coVerify { repo.save(match { set -> set.any { it.identifier == "f1" } }) }
        coVerify(exactly = 0) { repo.remove(any()) }
    }

    @Test
    fun `replace removes current filters then saves restored ones`() = runTest {
        every { repo.configs } returns flowOf(listOf(config("old")))
        val data = json.encodeToJsonElement(serializer, setOf(config("f1")))

        contributor.restore(data, RestoreMode.REPLACE)

        coVerify { repo.remove(setOf("old")) }
        coVerify { repo.save(match { set -> set.any { it.identifier == "f1" } }) }
    }

    @Test
    fun `snapshot serializes current filter configs`() = runTest {
        every { repo.configs } returns flowOf(listOf(config("f1")))

        val snap = contributor.snapshot()!!

        json.decodeFromJsonElement(serializer, snap).map { it.identifier } shouldBe listOf("f1")
    }

    @Test
    fun `snapshot is null when there are no custom filters`() = runTest {
        every { repo.configs } returns flowOf(emptyList())
        contributor.snapshot() shouldBe null
    }
}
