package eu.darken.sdmse.stats.core

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

/**
 * AffectedPkg.Action is persisted in affected_pkgs.action via Room's built-in enum support
 * (no explicit TypeConverter registered). Room serializes with .name and deserializes via valueOf,
 * so a rename of any entry here silently breaks existing user databases.
 *
 * Changing any string below is a breaking DB schema change.
 * See "Intentional rename workflow" before editing.
 */
class AffectedPkgActionContractTest : BaseTest() {

    private val persisted = mapOf(
        AffectedPkg.Action.EXPORTED to "EXPORTED",
        AffectedPkg.Action.STOPPED to "STOPPED",
        AffectedPkg.Action.ENABLED to "ENABLED",
        AffectedPkg.Action.DISABLED to "DISABLED",
        AffectedPkg.Action.DELETED to "DELETED",
        AffectedPkg.Action.ARCHIVED to "ARCHIVED",
        AffectedPkg.Action.RESTORED to "RESTORED",
    )

    @Test
    fun `golden mappings cover every enum value`() {
        AffectedPkg.Action.entries.size shouldBe persisted.size
    }

    @Test
    fun `enum name matches persisted string`() {
        persisted.forEach { (value, expected) -> value.name shouldBe expected }
    }

    @Test
    fun `persisted string resolves back via valueOf`() {
        persisted.forEach { (expected, stored) ->
            AffectedPkg.Action.valueOf(stored) shouldBe expected
        }
    }
}
