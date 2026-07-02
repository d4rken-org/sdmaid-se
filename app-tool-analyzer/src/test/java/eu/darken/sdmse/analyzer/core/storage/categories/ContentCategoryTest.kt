package eu.darken.sdmse.analyzer.core.storage.categories

import eu.darken.sdmse.analyzer.core.content.ContentGroup
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.storage.StorageId
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.util.UUID

class ContentCategoryTest : BaseTest() {

    private val storageId = StorageId(
        internalId = null,
        externalId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
    )
    private val group = ContentGroup(label = "group".toCaString())

    @Test
    fun `system content is always read-only`() {
        SystemCategory(storageId = storageId, groups = setOf(group)).isContentReadOnly shouldBe true
    }

    @Test
    fun `media content is read-only only in the degraded scan`() {
        MediaCategory(storageId = storageId, groups = setOf(group), isReadOnly = true)
            .isContentReadOnly shouldBe true
        MediaCategory(storageId = storageId, groups = setOf(group), isReadOnly = false)
            .isContentReadOnly shouldBe false
    }

    @Test
    fun `media content is writable by default`() {
        // Most call sites construct MediaCategory without isReadOnly — pin the default so a flipped
        // default can't silently make normal scans read-only (or degraded scans writable).
        val category = MediaCategory(storageId = storageId, groups = setOf(group))
        category.isReadOnly shouldBe false
        category.isContentReadOnly shouldBe false
    }

    @Test
    fun `app content is never read-only`() {
        AppCategory(storageId = storageId, pkgStats = emptyMap()).isContentReadOnly shouldBe false
    }

    @Test
    fun `ownsGroup matches only the owning category`() {
        val other = ContentGroup(label = "other".toCaString())
        val category = MediaCategory(storageId = storageId, groups = setOf(group))
        category.ownsGroup(group.id) shouldBe true
        category.ownsGroup(other.id) shouldBe false
    }
}
