package eu.darken.sdmse.systemcleaner.core.filter.custom.backup

import eu.darken.sdmse.common.backup.RestoreMode
import eu.darken.sdmse.systemcleaner.core.filter.custom.CustomFilterConfig
import eu.darken.sdmse.systemcleaner.core.filter.custom.CustomFilterRepo
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
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
    fun `replace swaps the whole filter set atomically via replaceAll`() = runTest {
        val data = json.encodeToJsonElement(serializer, setOf(config("f1")))

        contributor.restore(data, RestoreMode.REPLACE)

        coVerify { repo.replaceAll(match { set -> set.single().identifier == "f1" }) }
        coVerify(exactly = 0) { repo.remove(any()) }
        coVerify(exactly = 0) { repo.save(any()) }
    }

    @Test
    fun `validate decodes the section and rejects garbage`() = runTest {
        contributor.validate(json.encodeToJsonElement(serializer, setOf(config("f1"))))

        shouldThrow<Exception> { contributor.validate(JsonPrimitive("not a filter set")) }
    }

    @Test
    fun `validate rejects filter identifiers that could traverse the filesystem`() = runTest {
        listOf("../evil", "a/b", "a\\b", "..", "").forEach { id ->
            shouldThrow<IllegalArgumentException> {
                contributor.validate(json.encodeToJsonElement(serializer, setOf(config(id))))
            }
        }
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
