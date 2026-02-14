package eu.darken.sdmse.common.permissions

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class PermissionTest : BaseTest() {

    @Test
    fun `all permission IDs are unique`() {
        val ids = Permission.values.map { it.permissionId }
        ids.distinct().size shouldBe ids.size
    }

    @Test
    fun `fromId round-trips for all values`() {
        Permission.values.forEach { permission ->
            Permission.fromId(permission.permissionId) shouldBe permission
        }
    }

    @Test
    fun `values list contains all Permission subclasses`() {
        // Guard against forgetting to add new subclasses to the manual list.
        // If you add a new Permission subclass, also add it to Permission.values.
        val reflectedPermissions = Permission::class.nestedClasses
            .filter { it.isSealed || it.objectInstance is Permission }
            .mapNotNull { it.objectInstance }
            .filterIsInstance<Permission>()

        Permission.values.size shouldBe reflectedPermissions.size
    }
}
