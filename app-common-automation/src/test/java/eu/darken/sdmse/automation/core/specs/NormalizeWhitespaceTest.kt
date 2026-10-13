package eu.darken.sdmse.automation.core.specs

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class NormalizeWhitespaceTest : BaseTest() {

    @Test
    fun `line feed becomes a space`() {
        "Reminder\nFLEX".normalizeWhitespace() shouldBe "Reminder FLEX"
    }

    @Test
    fun `CRLF becomes a single space`() {
        "Reminder\r\nFLEX".normalizeWhitespace() shouldBe "Reminder FLEX"
    }

    @Test
    fun `tab becomes a space`() {
        "Reminder\tFLEX".normalizeWhitespace() shouldBe "Reminder FLEX"
    }

    @Test
    fun `NBSP becomes a regular space`() {
        "Reminder\u00A0FLEX".normalizeWhitespace() shouldBe "Reminder FLEX"
    }

    @Test
    fun `consecutive spaces collapse`() {
        "Reminder    FLEX".normalizeWhitespace() shouldBe "Reminder FLEX"
    }

    @Test
    fun `leading and trailing whitespace is trimmed`() {
        " \n Reminder FLEX \t".normalizeWhitespace() shouldBe "Reminder FLEX"
    }

    @Test
    fun `text without whitespace is unchanged`() {
        "ReminderFLEX".normalizeWhitespace() shouldBe "ReminderFLEX"
    }

    @Test
    fun `whitespace-only text becomes empty`() {
        " \n\t\u00A0 ".normalizeWhitespace() shouldBe ""
    }

    @Test
    fun `mixed run with another Unicode space separator collapses to one space`() {
        "Reminder\u2002\t\nFLEX".normalizeWhitespace() shouldBe "Reminder FLEX"
    }
}
