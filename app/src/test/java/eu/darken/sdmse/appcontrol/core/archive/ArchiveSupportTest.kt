package eu.darken.sdmse.appcontrol.core.archive

import android.content.Context
import android.content.pm.PackageManager
import eu.darken.sdmse.common.BuildWrap
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class ArchiveSupportTest : BaseTest() {

    @MockK lateinit var context: Context
    @MockK lateinit var packageManager: PackageManager

    private lateinit var archiveSupport: ArchiveSupport

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        mockkObject(BuildWrap)
        mockkObject(BuildWrap.VERSION)

        every { context.packageManager } returns packageManager

        archiveSupport = ArchiveSupport(context)
    }

    @AfterEach
    fun teardown() {
        unmockkObject(BuildWrap)
        unmockkObject(BuildWrap.VERSION)
    }

    @Test
    fun `returns false when API level is below 35`() {
        every { BuildWrap.VERSION.SDK_INT } returns 34
        every { BuildWrap.VERSION.CODENAME } returns "REL"

        archiveSupport.isArchivingEnabled() shouldBe false
    }

    @Test
    fun `returns true when API is 35 and Flags class not found`() {
        every { BuildWrap.VERSION.SDK_INT } returns 35
        every { BuildWrap.VERSION.CODENAME } returns "REL"

        // Since Flags class won't exist in test environment, assume archiving is available on API 35+
        archiveSupport.isArchivingEnabled() shouldBe true
    }

    @Test
    fun `handles VanillaIceCream codename for API 35`() {
        every { BuildWrap.VERSION.SDK_INT } returns 34
        every { BuildWrap.VERSION.CODENAME } returns "VanillaIceCream"

        // Even with the codename, Flags class won't exist - assume archiving is available on API 35+
        archiveSupport.isArchivingEnabled() shouldBe true
    }
}
