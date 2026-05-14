package eu.darken.sdmse.common.compose.settings

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class SettingsSwitchItemGatedTest : BaseComposeRobolectricTest() {

    @Test
    fun `gated row tap invokes onUpgrade and never onCheckedChange`() {
        var upgradeCount = 0
        composeRule.setContent {
            PreviewWrapper {
                SettingsSwitchItem(
                    title = "Pro feature",
                    subtitle = null,
                    checked = false,
                    onCheckedChange = { fail("onCheckedChange must not fire when requiresUpgrade=true") },
                    requiresUpgrade = true,
                    onUpgrade = { upgradeCount++ },
                )
            }
        }

        composeRule.onNodeWithText("Pro feature").performClick()
        composeRule.runOnIdle { assertEquals(1, upgradeCount) }
    }

    @Test
    fun `ungated row tap invokes onCheckedChange with the toggled value`() {
        var received: Boolean? = null
        composeRule.setContent {
            PreviewWrapper {
                SettingsSwitchItem(
                    title = "Normal feature",
                    subtitle = null,
                    checked = false,
                    onCheckedChange = { received = it },
                )
            }
        }

        composeRule.onNodeWithText("Normal feature").performClick()
        composeRule.runOnIdle { assertEquals(true, received) }
    }
}
