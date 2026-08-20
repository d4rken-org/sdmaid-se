package eu.darken.sdmse.common.compose

import android.content.res.Configuration
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

/**
 * The focus ring's hardware gate.
 *
 * `InputMode.Keyboard` on its own is not evidence of a remote: Compose mirrors the platform's
 * touch-mode flag, and any navigation key event clears that flag display-wide. AppCleaner injects
 * DPAD keys to clear caches on Android 16+ Pixels, so the app used to switch its own TV focus ring
 * on for touch-only phone users. These cases pin the signals that gate it.
 */
class FocusHighlightInputTest : BaseTest() {

    private data class Device(
        val navigation: Int = Configuration.NAVIGATION_NONAV,
        val keyboard: Int = Configuration.KEYBOARD_NOKEYS,
        val uiMode: Int = Configuration.UI_MODE_TYPE_NORMAL,
        val isTvLike: Boolean = false,
    )

    private fun Device.supportsRing() =
        supportsFocusHighlightInput(navigation, keyboard, uiMode, isTvLike)

    @Test
    fun `a touch-only phone gets no ring`() {
        Device().supportsRing() shouldBe false
    }

    @Test
    fun `an attached d-pad counts`() {
        Device(navigation = Configuration.NAVIGATION_DPAD).supportsRing() shouldBe true
    }

    @Test
    fun `an attached hardware keyboard counts`() {
        Device(keyboard = Configuration.KEYBOARD_QWERTY).supportsRing() shouldBe true
    }

    @Test
    fun `a television ui mode counts`() {
        Device(uiMode = Configuration.UI_MODE_TYPE_TELEVISION).supportsRing() shouldBe true
    }

    @Test
    fun `a leanback box counts even when it reports no d-pad and no tv ui mode`() {
        // The config_hasPermanentDpad fallback that covers standard Android TV is an overlay
        // resource, not an API guarantee, so FEATURE_LEANBACK is the floor for third-party boxes.
        Device(isTvLike = true).supportsRing() shouldBe true
    }

    @Test
    fun `uiMode is masked, so night mode does not look like a television`() {
        val darkPhone = Device(
            uiMode = Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_YES,
        )
        darkPhone.supportsRing() shouldBe false
    }

    @Test
    fun `a television in night mode still counts`() {
        val darkTv = Device(
            uiMode = Configuration.UI_MODE_TYPE_TELEVISION or Configuration.UI_MODE_NIGHT_YES,
        )
        darkTv.supportsRing() shouldBe true
    }

    @Test
    fun `trackball and wheel are deliberately excluded`() {
        Device(navigation = Configuration.NAVIGATION_TRACKBALL).supportsRing() shouldBe false
        Device(navigation = Configuration.NAVIGATION_WHEEL).supportsRing() shouldBe false
    }

    @Test
    fun `a device reporting only non-alphabetic keys does not count`() {
        // Volume and power keys surface as a non-alphabetic keyboard on some devices; AOSP only
        // sets KEYBOARD_QWERTY for KEYBOARD_TYPE_ALPHABETIC, and that is not focus-movable input.
        Device(keyboard = Configuration.KEYBOARD_12KEY).supportsRing() shouldBe false
    }
}
