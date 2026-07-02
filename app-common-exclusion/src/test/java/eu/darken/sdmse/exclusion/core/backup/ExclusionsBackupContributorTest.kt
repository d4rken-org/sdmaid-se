package eu.darken.sdmse.exclusion.core.backup

import eu.darken.sdmse.common.backup.RestoreMode
import eu.darken.sdmse.exclusion.core.ExclusionImporter
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.ExclusionStorage
import eu.darken.sdmse.exclusion.core.types.Exclusion
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class ExclusionsBackupContributorTest : BaseTest() {

    private val exclusionStorage = mockk<ExclusionStorage>()
    private val exclusionManager = mockk<ExclusionManager>(relaxed = true)
    private val exclusionImporter = mockk<ExclusionImporter>()
    private val json = Json
    private val contributor =
        ExclusionsBackupContributor(exclusionStorage, exclusionManager, exclusionImporter, json)

    private val containerJson = """{"exclusionRaw":"[]","version":1}"""

    @Test
    fun `replace mode atomically replaces user exclusions`() = runTest {
        val restored = setOf(mockk<Exclusion>())
        coEvery { exclusionImporter.import("payload") } returns restored

        // Legacy quoted-string shape is still accepted.
        contributor.restore(JsonPrimitive("payload"), RestoreMode.REPLACE)

        coVerify { exclusionManager.replaceUserExclusions(restored) }
        coVerify(exactly = 0) { exclusionManager.save(any()) }
    }

    @Test
    fun `merge mode saves without clearing`() = runTest {
        val restored = setOf(mockk<Exclusion>())
        coEvery { exclusionImporter.import("payload") } returns restored

        contributor.restore(JsonPrimitive("payload"), RestoreMode.MERGE)

        coVerify { exclusionManager.save(restored) }
        coVerify(exactly = 0) { exclusionManager.replaceUserExclusions(any()) }
    }

    @Test
    fun `restore accepts the json object shape`() = runTest {
        val restored = setOf(mockk<Exclusion>())
        coEvery { exclusionImporter.import(containerJson) } returns restored

        contributor.restore(json.parseToJsonElement(containerJson), RestoreMode.MERGE)

        coVerify { exclusionManager.save(restored) }
    }

    @Test
    fun `snapshot stores the importer output as a json object`() = runTest {
        val current = setOf(mockk<Exclusion>())
        coEvery { exclusionStorage.load() } returns current
        coEvery { exclusionImporter.export(current) } returns containerJson

        contributor.snapshot() shouldBe json.parseToJsonElement(containerJson)
    }

    @Test
    fun `snapshot is null when there are no exclusions`() = runTest {
        coEvery { exclusionStorage.load() } returns emptySet()
        contributor.snapshot() shouldBe null
    }
}
