package eu.darken.sdmse.setup.root

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RootSetupModuleTest {

    @Test
    fun `completion depends on user choice and service readiness`() {
        assertFalse(RootSetupModule.Result(useRoot = null).isComplete)
        assertTrue(RootSetupModule.Result(useRoot = false).isComplete)
        assertFalse(
            RootSetupModule.Result(
                useRoot = true,
                isInstalled = true,
                ourService = false,
            ).isComplete,
        )
        assertTrue(
            RootSetupModule.Result(
                useRoot = true,
                isInstalled = false,
                ourService = false,
            ).isComplete,
        )
        assertTrue(
            RootSetupModule.Result(
                useRoot = true,
                isInstalled = true,
                ourService = true,
            ).isComplete,
        )
    }
}
