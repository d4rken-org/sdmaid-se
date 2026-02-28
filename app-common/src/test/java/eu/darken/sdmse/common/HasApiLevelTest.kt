package eu.darken.sdmse.common

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class HasApiLevelTest : BaseTest() {

    @BeforeEach
    fun setup() {
        mockkObject(BuildWrap)
        mockkObject(BuildWrap.VERSION)
    }

    @AfterEach
    fun teardown() {
        unmockkObject(BuildWrap)
        unmockkObject(BuildWrap.VERSION)
    }

    @Test
    fun `returns true when SDK_INT is at or above requested level`() {
        every { BuildWrap.VERSION.SDK_INT } returns 36
        every { BuildWrap.VERSION.CODENAME } returns "REL"

        hasApiLevel(36) shouldBe true
        hasApiLevel(35) shouldBe true
        hasApiLevel(26) shouldBe true
    }

    @Test
    fun `returns false when SDK_INT is below requested level and no matching codename`() {
        every { BuildWrap.VERSION.SDK_INT } returns 35
        every { BuildWrap.VERSION.CODENAME } returns "REL"

        hasApiLevel(36) shouldBe false
    }

    @Test
    fun `detects API 36 via Baklava codename on preview device`() {
        every { BuildWrap.VERSION.SDK_INT } returns 35
        every { BuildWrap.VERSION.CODENAME } returns "Baklava"

        hasApiLevel(36) shouldBe true
    }

    @Test
    fun `detects API 35 via VanillaIceCream codename on preview device`() {
        every { BuildWrap.VERSION.SDK_INT } returns 34
        every { BuildWrap.VERSION.CODENAME } returns "VanillaIceCream"

        hasApiLevel(35) shouldBe true
    }

    @Test
    fun `detects API 34 via UpsideDownCake codename on preview device`() {
        every { BuildWrap.VERSION.SDK_INT } returns 33
        every { BuildWrap.VERSION.CODENAME } returns "UpsideDownCake"

        hasApiLevel(34) shouldBe true
    }

    @Test
    fun `detects API 37 via CinnamonBun codename on preview device`() {
        every { BuildWrap.VERSION.SDK_INT } returns 36
        every { BuildWrap.VERSION.CODENAME } returns "CinnamonBun"

        hasApiLevel(37) shouldBe true
    }

    @Test
    fun `Baklava codename does not match wrong API level`() {
        every { BuildWrap.VERSION.SDK_INT } returns 35
        every { BuildWrap.VERSION.CODENAME } returns "Baklava"

        hasApiLevel(37) shouldBe false
    }
}
