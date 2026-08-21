package eu.darken.sdmse.main.ui.dashboard.bottom

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ByteFormatter
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.main.ui.dashboard.BottomBarState
import eu.darken.sdmse.main.ui.dashboard.HeroSummary
import eu.darken.sdmse.main.ui.dashboard.showsUpgradeBlock
import eu.darken.sdmse.main.core.SDMTool
import io.kotest.matchers.shouldBe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config
import testhelpers.compose.BaseComposeRobolectricTest
import java.time.Instant

class DashboardHeroCardTest : BaseComposeRobolectricTest() {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun summary(
        timestamp: Instant? = null,
        itemCount: Int = 37,
    ) = HeroSummary(
        mode = HeroSummary.Mode.FREEABLE,
        totalSize = 2L * 1024 * 1024 * 1024,
        itemCount = itemCount,
        tools = listOf(
            HeroSummary.ToolSlice(SDMTool.Type.CORPSEFINDER, 1L * 1024 * 1024 * 1024, 12),
            HeroSummary.ToolSlice(SDMTool.Type.SYSTEMCLEANER, 1L * 1024 * 1024 * 1024, 25),
        ),
        timestamp = timestamp,
    )

    private fun deleteState(
        hero: HeroSummary?,
        now: Instant = Instant.EPOCH,
    ) = BottomBarState(
        isReady = true,
        actionState = BottomBarState.Action.DELETE,
        activeTasks = 0,
        queuedTasks = 0,
        heroSummary = hero,
        upgradeInfo = null,
        now = now,
    )

    @Test
    fun `hero shows the freeable caption when visible`() {
        composeRule.setContent {
            PreviewWrapper {
                BottomBar(
                    state = deleteState(summary()),
                    isVisible = true,
                    heroVisible = true,
                    onMainAction = {},
                    onSettings = {},
                    onUpgrade = {},
                    onDismissHero = {},
                )
            }
        }
        composeRule.onNodeWithText("can be removed", substring = true).assertExists()
    }

    private fun renderHero(
        hero: HeroSummary,
        fontScale: Float = 1f,
        now: Instant = Instant.EPOCH,
        onUpgrade: () -> Unit = {},
        onLockedToolClick: (SDMTool.Type) -> Unit = {},
    ) {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                PreviewWrapper {
                BottomBar(
                    state = deleteState(hero, now = now),
                    isVisible = true,
                    heroVisible = true,
                    onMainAction = {},
                    onSettings = {},
                    onUpgrade = onUpgrade,
                    onDismissHero = {},
                    onLockedToolClick = onLockedToolClick,
                    )
                }
            }
        }
    }

    @Test
    fun `freeable caption uses the singular form for a single item`() {
        renderHero(summary(itemCount = 1))
        composeRule.onNodeWithText("1 item can be removed", substring = true).assertExists()
    }

    // Regression: the caption used to splice an already-pluralized noun phrase into a plain <string>,
    // so German was locked to the singular verb and rendered "10 Elemente kann entfernt werden".
    //
    // Expected text is resolved through the resources rather than hardcoded — values-de is a Crowdin
    // download target, so pinning wording would make a translator reword or a crowdin pull fail CI.
    // Matching is exact and positive-only: a substring check can spuriously fail when one form
    // legitimately contains the other, and asserting the absence of the opposing form breaks on the
    // impersonal rephrasings that make both forms identical (several locales already do this). An
    // exact match on the form for the real count already fails if the wrong form is selected.
    @Test
    @Config(qualifiers = "de")
    fun `german caption picks the plural form for multiple items`() {
        val expected = context.resources.getQuantityString(R.plurals.dashboard_hero_freeable_x_items, 10, 10)
        renderHero(summary(itemCount = 10))
        composeRule.onNodeWithText(expected).assertExists()
    }

    @Test
    @Config(qualifiers = "de")
    fun `german caption picks the singular form for a single item`() {
        val expected = context.resources.getQuantityString(R.plurals.dashboard_hero_freeable_x_items, 1, 1)
        renderHero(summary(itemCount = 1))
        composeRule.onNodeWithText(expected).assertExists()
    }

    @Test
    fun `tapping dismiss invokes the callback`() {
        var dismissed = 0
        composeRule.setContent {
            PreviewWrapper {
                BottomBar(
                    state = deleteState(summary()),
                    isVisible = true,
                    heroVisible = true,
                    onMainAction = {},
                    onSettings = {},
                    onUpgrade = {},
                    onDismissHero = { dismissed++ },
                )
            }
        }
        composeRule.onNodeWithContentDescription("Dismiss").performClick()
        composeRule.runOnIdle { assertEquals(1, dismissed) }
    }

    @Test
    fun `freed-mode hero shows the freed caption`() {
        val freed = HeroSummary(
            mode = HeroSummary.Mode.FREED,
            totalSize = 1L * 1024 * 1024 * 1024,
            itemCount = 12,
            tools = listOf(
                HeroSummary.ToolSlice(SDMTool.Type.CORPSEFINDER, 1L * 1024 * 1024 * 1024, 12),
            ),
        )
        composeRule.setContent {
            PreviewWrapper {
                BottomBar(
                    state = deleteState(freed),
                    isVisible = true,
                    heroVisible = true,
                    onMainAction = {},
                    onSettings = {},
                    onUpgrade = {},
                    onDismissHero = {},
                )
            }
        }
        composeRule.onNodeWithText("items removed", substring = true).assertExists()
    }

    @Test
    fun `freed-mode hero renders what the cleanup left behind`() {
        val freed = HeroSummary(
            mode = HeroSummary.Mode.FREED,
            totalSize = 1L * 1024 * 1024 * 1024,
            itemCount = 12,
            tools = listOf(
                HeroSummary.ToolSlice(SDMTool.Type.CORPSEFINDER, 1L * 1024 * 1024 * 1024, 12),
            ),
            residueSize = 5L * 1024 * 1024,
            residueCount = 2,
        )
        renderHero(freed)
        composeRule.onNodeWithText("left", substring = true).assertExists()
    }

    @Test
    fun `the leftover line survives the worst case the card is sized for`() {
        // The card body is a fixed height that only scales with the font scale, so every line
        // competes for the same budget. Worst case: all four tools charted (chips wrap to a second
        // row), a leftover line, and Android's largest accessibility font scale. If the budget is
        // too tight, the last things laid out drop off the bottom — so assert the leftover line and
        // the hint below it are actually on screen, not merely composed.
        //
        // Font scale 2.0 ONLY — do NOT add a 1.0 sibling. One was written and removed after being
        // measured: this harness has stub font metrics, so it cannot answer that question. A
        // bodyMedium line measures 36.0dp here against a real line height of 20dp, and text width is
        // a flat 1.0dp-per-character stub that ignores the font scale entirely (the headline
        // "2.00 GB freed" measures 13.0dp wide), so nothing ever wraps. At 1.0 those inflated line
        // heights push the hint off the card in the harness while it renders fine on a device — the
        // test would fail for a reason that does not exist. At 2.0 the fixed-dp parts of the body
        // are proportionally small enough that the distortion no longer decides the outcome.
        val freed = HeroSummary(
            mode = HeroSummary.Mode.FREED,
            totalSize = 2L * 1024 * 1024 * 1024,
            itemCount = 8147,
            tools = listOf(
                HeroSummary.ToolSlice(SDMTool.Type.CORPSEFINDER, 129L * 1024, 12),
                HeroSummary.ToolSlice(SDMTool.Type.SYSTEMCLEANER, 852L * 1024 * 1024, 51),
                HeroSummary.ToolSlice(SDMTool.Type.APPCLEANER, 87L * 1024 * 1024, 498),
                HeroSummary.ToolSlice(SDMTool.Type.DEDUPLICATOR, 32L * 1024 * 1024, 60),
            ),
            residueSize = 5L * 1024 * 1024,
            residueCount = 2,
        )
        renderHero(freed, fontScale = 2f)

        composeRule.onNodeWithText("left", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.dashboard_hero_freed_hint)).assertIsDisplayed()
    }

    @Test
    fun `freed-mode hero omits the leftover line when nothing was left`() {
        // The common outcome is a clean sweep; a "0 B left" line would be noise on every one of them.
        val freed = HeroSummary(
            mode = HeroSummary.Mode.FREED,
            totalSize = 1L * 1024 * 1024 * 1024,
            itemCount = 12,
            tools = listOf(
                HeroSummary.ToolSlice(SDMTool.Type.CORPSEFINDER, 1L * 1024 * 1024 * 1024, 12),
            ),
        )
        renderHero(freed)
        composeRule.onNodeWithText("left", substring = true).assertDoesNotExist()
    }

    @Test
    fun `nothing-freed hero states the outcome without printing a zero size`() {
        val nothingFreed = HeroSummary(
            mode = HeroSummary.Mode.NOTHING_FREED,
            totalSize = 0L,
            itemCount = 0,
            tools = emptyList(),
        )
        composeRule.setContent {
            PreviewWrapper {
                BottomBar(
                    state = deleteState(nothingFreed),
                    isVisible = true,
                    heroVisible = true,
                    onMainAction = {},
                    onSettings = {},
                    onUpgrade = {},
                    onDismissHero = {},
                )
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.dashboard_hero_nothing_freed_headline)).assertExists()
        // The size-interpolating headline path would render "0 B" here; this mode must not.
        composeRule.onNodeWithText(
            ByteFormatter.formatSize(context, 0L).first,
            substring = true,
        ).assertDoesNotExist()
    }

    @Test
    fun `activating a tool chip invokes onToolClick with the rendered mode and tool`() {
        var clickedMode: HeroSummary.Mode? = null
        var clickedType: SDMTool.Type? = null
        composeRule.setContent {
            PreviewWrapper {
                BottomBar(
                    state = deleteState(summary()),
                    isVisible = true,
                    heroVisible = true,
                    onMainAction = {},
                    onSettings = {},
                    onUpgrade = {},
                    onDismissHero = {},
                    onToolClick = { mode, type -> clickedMode = mode; clickedType = type },
                )
            }
        }
        // Select by the tool name (the chip's accessible name), not the size — two tools can share a size.
        // Drive the wired click via the node's semantics action: Robolectric's synthetic pointer click
        // doesn't reliably dispatch to small clickable Surfaces inside the offset/alpha hero layer, but
        // the chip IS exposed as a clickable button (asserted) and the real tap is verified on-device.
        val corpseName = context.getString(CommonR.string.corpsefinder_tool_name)
        composeRule.onNodeWithContentDescription(corpseName)
            .assertHasClickAction()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle {
            assertEquals(HeroSummary.Mode.FREEABLE, clickedMode)
            assertEquals(SDMTool.Type.CORPSEFINDER, clickedType)
        }
    }

    @Test
    fun `freed-mode chip click reports the freed mode`() {
        var clickedMode: HeroSummary.Mode? = null
        val freed = HeroSummary(
            mode = HeroSummary.Mode.FREED,
            totalSize = 1L * 1024 * 1024 * 1024,
            itemCount = 12,
            tools = listOf(
                HeroSummary.ToolSlice(SDMTool.Type.CORPSEFINDER, 1L * 1024 * 1024 * 1024, 12),
            ),
        )
        composeRule.setContent {
            PreviewWrapper {
                BottomBar(
                    state = deleteState(freed),
                    isVisible = true,
                    heroVisible = true,
                    onMainAction = {},
                    onSettings = {},
                    onUpgrade = {},
                    onDismissHero = {},
                    onToolClick = { mode, _ -> clickedMode = mode },
                )
            }
        }
        val corpseName = context.getString(CommonR.string.corpsefinder_tool_name)
        composeRule.onNodeWithContentDescription(corpseName)
            .assertHasClickAction()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle { assertEquals(HeroSummary.Mode.FREED, clickedMode) }
    }

    @Test
    fun `freeable hero shows the review hint, freed hero shows the removed hint`() {
        composeRule.setContent {
            PreviewWrapper {
                BottomBar(
                    state = deleteState(summary()),
                    isVisible = true,
                    heroVisible = true,
                    onMainAction = {},
                    onSettings = {},
                    onUpgrade = {},
                    onDismissHero = {},
                )
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.dashboard_hero_freeable_hint)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.dashboard_hero_freed_hint)).assertDoesNotExist()
    }

    @Test
    fun `dismissed hero collapses - caption is gone`() {
        composeRule.setContent {
            PreviewWrapper {
                BottomBar(
                    state = deleteState(summary()),
                    isVisible = true,
                    heroVisible = false,
                    onMainAction = {},
                    onSettings = {},
                    onUpgrade = {},
                    onDismissHero = {},
                )
            }
        }
        composeRule.onNodeWithText("can be removed", substring = true).assertDoesNotExist()
    }

    @Test
    fun `tapping the compact bar chip expands a collapsed hero`() {
        var expanded = 0
        val hero = summary()
        composeRule.setContent {
            PreviewWrapper {
                BottomBar(
                    state = deleteState(hero),
                    isVisible = true,
                    // Collapsed: the floating hero is gone and the bar shows the compact chip instead.
                    heroVisible = false,
                    canExpandHero = true,
                    onMainAction = {},
                    onSettings = {},
                    onUpgrade = {},
                    onDismissHero = {},
                    onExpandHero = { expanded++ },
                )
            }
        }
        val label = ByteFormatter.formatSize(context, hero.totalSize).first
        composeRule.onNodeWithText(label)
            .assertHasClickAction()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle { assertEquals(1, expanded) }
    }

    @Test
    fun `the compact chip is clickable for a hero that was never expanded`() {
        // Card-triggered results never auto-show the hero; the chip is the way back to it, and it
        // must not depend on the user having dismissed something first.
        var expanded = 0
        val hero = summary()
        composeRule.setContent {
            PreviewWrapper {
                BottomBar(
                    state = deleteState(hero),
                    isVisible = true,
                    heroVisible = false,
                    canExpandHero = true,
                    onMainAction = {},
                    onSettings = {},
                    onUpgrade = {},
                    onDismissHero = {},
                    onExpandHero = { expanded++ },
                )
            }
        }
        composeRule.onNodeWithText(ByteFormatter.formatSize(context, hero.totalSize).first)
            .assertHasClickAction()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle { assertEquals(1, expanded) }
    }

    @Test
    fun `the compact chip is passive during a tour`() {
        // The tour suppresses the floating hero on purpose, so the chip must not be able to bring
        // it back and fight the tour's targets.
        val hero = summary()
        composeRule.setContent {
            PreviewWrapper {
                BottomBar(
                    state = deleteState(hero),
                    isVisible = true,
                    heroVisible = false,
                    canExpandHero = false,
                    onMainAction = {},
                    onSettings = {},
                    onUpgrade = {},
                    onDismissHero = {},
                    onExpandHero = {},
                )
            }
        }
        composeRule.onNodeWithText(ByteFormatter.formatSize(context, hero.totalSize).first)
            .assertHasNoClickAction()
    }

    @Test
    fun `freeable hero shows the discard button and tapping it invokes the callback`() {
        var discarded = 0
        composeRule.setContent {
            PreviewWrapper {
                BottomBar(
                    state = deleteState(summary()),
                    isVisible = true,
                    heroVisible = true,
                    onMainAction = {},
                    onSettings = {},
                    onUpgrade = {},
                    onDismissHero = {},
                    onDiscardResults = { discarded++ },
                )
            }
        }
        composeRule.onNodeWithText(context.getString(CommonR.string.general_discard_action))
            .assertHasClickAction()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle { assertEquals(1, discarded) }
    }

    @Test
    fun `freed-mode hero offers discard too, since its X only collapses it into the bar chip`() {
        var discarded = 0
        val freed = HeroSummary(
            mode = HeroSummary.Mode.FREED,
            totalSize = 1L * 1024 * 1024 * 1024,
            itemCount = 12,
            tools = listOf(
                HeroSummary.ToolSlice(SDMTool.Type.CORPSEFINDER, 1L * 1024 * 1024 * 1024, 12),
            ),
        )
        composeRule.setContent {
            PreviewWrapper {
                BottomBar(
                    state = deleteState(freed),
                    isVisible = true,
                    heroVisible = true,
                    onMainAction = {},
                    onSettings = {},
                    onUpgrade = {},
                    onDismissHero = {},
                    onDiscardResults = { discarded++ },
                )
            }
        }
        composeRule.onNodeWithText(context.getString(CommonR.string.general_discard_action))
            .assertHasClickAction()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle { assertEquals(1, discarded) }
    }

    @Test
    fun `hero footer renders the relative result age`() {
        val now = Instant.parse("2026-06-10T12:00:00Z")
        val scannedAt = now.minusSeconds(5 * 60)
        // Same call the card makes — locale-proof expectation.
        val expected = DateUtils.getRelativeTimeSpanString(
            scannedAt.toEpochMilli(),
            now.toEpochMilli(),
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()
        composeRule.setContent {
            PreviewWrapper {
                BottomBar(
                    state = deleteState(summary(timestamp = scannedAt), now = now),
                    isVisible = true,
                    heroVisible = true,
                    onMainAction = {},
                    onSettings = {},
                    onUpgrade = {},
                    onDismissHero = {},
                )
            }
        }
        composeRule.onNodeWithText(expected).assertExists()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.dashboard_hero_scanned_timestamp_description, expected),
        ).assertExists()
    }

    @Test
    fun `hero footer shows no timestamp when the summary has none`() {
        composeRule.setContent {
            PreviewWrapper {
                BottomBar(
                    state = deleteState(summary(timestamp = null)),
                    isVisible = true,
                    heroVisible = true,
                    onMainAction = {},
                    onSettings = {},
                    onUpgrade = {},
                    onDismissHero = {},
                )
            }
        }
        composeRule.onNodeWithText("ago", substring = true).assertDoesNotExist()
    }

    private val appCleanerName get() = context.getString(CommonR.string.appcleaner_tool_name)
    private val deduplicatorName get() = context.getString(CommonR.string.deduplicator_tool_name)

    private fun lockedOnly(lockedSize: Long = 512L * 1024 * 1024, lockedCount: Int = 9) = HeroSummary(
        mode = HeroSummary.Mode.LOCKED_ONLY,
        totalSize = 0L,
        itemCount = 0,
        tools = emptyList(),
        lockedTools = listOf(HeroSummary.ToolSlice(SDMTool.Type.APPCLEANER, lockedSize, lockedCount)),
    )

    @Test
    fun `locked findings render as a chip identified by the tool name`() {
        val locked = lockedOnly()
        renderHero(locked)

        composeRule.onNodeWithContentDescription(appCleanerName).assertIsDisplayed()
        composeRule.onNodeWithText(ByteFormatter.formatSize(context, locked.lockedSize).first).assertExists()
    }

    @Test
    fun `a locked chip routes through onLockedToolClick, not the mode-routed tool click`() {
        // A locked tool has no report to open — it was never cleaned — so it must not share the
        // chip callback that routes FREED chips to reports.
        var lockedClicks = 0
        var clickedType: SDMTool.Type? = null
        renderHero(lockedOnly(), onLockedToolClick = { lockedClicks++; clickedType = it })

        composeRule.onNodeWithContentDescription(appCleanerName)
            .assertHasClickAction()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle {
            assertEquals(1, lockedClicks)
            assertEquals(SDMTool.Type.APPCLEANER, clickedType)
        }
    }

    @Test
    fun `the locked-only hero headlines the locked size and shows no freeable chips`() {
        val locked = lockedOnly()
        renderHero(locked)

        // The headline splices in the *locked* size — totalSize is 0 in this mode by design, and
        // the size-interpolating path would otherwise print "0 B".
        composeRule.onNodeWithText(
            context.getString(
                R.string.dashboard_hero_locked_headline,
                ByteFormatter.formatSize(context, locked.lockedSize).first,
            ),
        ).assertExists()
        composeRule.onNodeWithContentDescription(
            context.getString(CommonR.string.corpsefinder_tool_name),
        ).assertDoesNotExist()
    }

    @Test
    fun `tapping the upsell line opens the upgrade screen`() {
        var upgrades = 0
        renderHero(lockedOnly(), onUpgrade = { upgrades++ })

        composeRule.onNodeWithText(context.getString(R.string.dashboard_hero_locked_hint))
            .assertHasClickAction()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle { assertEquals(1, upgrades) }
    }

    @Test
    fun `a nothing-freed hero keeps its own hint while still showing locked chips`() {
        // That hint diagnoses why the cleanup came up empty; an unlock line must not displace it.
        // With no free chips there is nothing for a nested block to be additional to, so the locked
        // chips stay flat in the main row and the card keeps its base height.
        val nothingFreed = HeroSummary(
            mode = HeroSummary.Mode.NOTHING_FREED,
            totalSize = 0L,
            itemCount = 0,
            tools = emptyList(),
            lockedTools = listOf(HeroSummary.ToolSlice(SDMTool.Type.APPCLEANER, 512L * 1024 * 1024, 9)),
        )
        nothingFreed.showsUpgradeBlock shouldBe false
        renderHero(nothingFreed)

        composeRule.onNodeWithText(context.getString(R.string.dashboard_hero_nothing_freed_hint)).assertExists()
        composeRule.onNodeWithContentDescription(appCleanerName).assertIsDisplayed()
        composeRule.onNodeWithText(blockCaption).assertDoesNotExist()
    }

    @Test
    fun `a locked-only hero renders flat, without the nested block`() {
        val locked = lockedOnly()
        locked.showsUpgradeBlock shouldBe false
        renderHero(locked)

        composeRule.onNodeWithContentDescription(appCleanerName).assertIsDisplayed()
        composeRule.onNodeWithText(blockCaption).assertDoesNotExist()
    }

    @Test
    fun `the collapsed bar chip shows the locked size instead of zero`() {
        val locked = lockedOnly()
        composeRule.setContent {
            PreviewWrapper {
                BottomBar(
                    state = deleteState(locked),
                    isVisible = true,
                    heroVisible = false,
                    canExpandHero = true,
                    onMainAction = {},
                    onSettings = {},
                    onUpgrade = {},
                    onDismissHero = {},
                )
            }
        }
        composeRule.onNodeWithText(ByteFormatter.formatSize(context, locked.lockedSize).first).assertExists()
        composeRule.onNodeWithText(ByteFormatter.formatSize(context, 0L).first).assertDoesNotExist()
        // The star is not decorative here: without this the chip announces a bare size with no
        // indication that the space is out of reach.
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.dashboard_hero_locked_chip_description),
            substring = true,
        ).assertExists()
    }

    private val blockCaption get() = context.getString(R.string.dashboard_hero_locked_block_caption)

    private val freedAt = Instant.parse("2026-06-10T12:00:00Z")

    private fun freedWithLocked() = HeroSummary(
        mode = HeroSummary.Mode.FREED,
        totalSize = 2L * 1024 * 1024 * 1024,
        itemCount = 8147,
        tools = listOf(
            HeroSummary.ToolSlice(SDMTool.Type.CORPSEFINDER, 129L * 1024, 12),
            HeroSummary.ToolSlice(SDMTool.Type.SYSTEMCLEANER, 852L * 1024 * 1024, 51),
        ),
        timestamp = freedAt,
        residueSize = 5L * 1024 * 1024,
        residueCount = 2,
        lockedTools = listOf(
            HeroSummary.ToolSlice(SDMTool.Type.APPCLEANER, 87L * 1024 * 1024, 498),
            HeroSummary.ToolSlice(SDMTool.Type.DEDUPLICATOR, 32L * 1024 * 1024, 60),
        ),
    )

    @Test
    fun `the nested upgrade block and everything under it survive the largest font scale`() {
        // The fullest the card ever gets: a freed result with a leftover line and its own chip row,
        // plus the nested block with two more chips, at Android's largest accessibility font scale.
        // The block is what the taller base height exists for, so assert its caption, its chips, the
        // leftover line above it and the footer below it are all on screen rather than merely
        // composed. Font scale 2.0 only — see the note on the sibling test above.
        val hero = freedWithLocked()
        val now = freedAt.plusSeconds(5 * 60)
        hero.showsUpgradeBlock shouldBe true
        renderHero(hero, fontScale = 2f, now = now)

        composeRule.onNodeWithText(blockCaption).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(appCleanerName).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            context.getString(CommonR.string.deduplicator_tool_name),
        ).assertIsDisplayed()
        composeRule.onNodeWithText("left", substring = true).assertIsDisplayed()
        // The footer sits below everything; if the block overran the budget this is what it buries.
        val relativeTime = DateUtils.getRelativeTimeSpanString(
            freedAt.toEpochMilli(),
            now.toEpochMilli(),
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()
        composeRule.onNodeWithText(relativeTime).assertIsDisplayed()
    }

    /**
     * A FREED summary carrying three free chips beside the block. Reachable despite the two-chip
     * ceiling that holds for FREEABLE: FREED summaries are not built by `buildHeroSummary` at all —
     * `accumulateFreed` assembles their tools from whichever tools ran, with no entitlement filter,
     * and `lockedTools` is recomputed from live inputs on every emission, filtered only against the
     * tools the run submitted to.
     *
     * One reachable path: a Pro user cleans CorpseFinder, SystemCleaner and AppCleaner while
     * Deduplicator is disabled in one-tap; afterward the entitlement lapses and Deduplicator is
     * enabled while its findings remain. The three cleaned slices stay in `tools`, while the
     * never-submitted Deduplicator enters `lockedTools`.
     *
     * Every clause of that is load-bearing. `lockedSlices` returns nothing at all while `isPro`, so
     * the lapse alone is not enough; and had Deduplicator been enabled with findings during the
     * cleanup, the DELETE branch would have submitted it, after which the settled path's filter
     * against the run's submitted set drops it from `lockedTools` again.
     *
     * The fail-open `isProForUi()` read reaches the same shape, but only when the dashboard's own
     * upgrade flow reports non-Pro AND Deduplicator independently satisfies `lockedSlices`: enabled,
     * with findings, never submitted.
     */
    private fun freedWithThreeFreeAndLocked(): HeroSummary {
        val tools = listOf(
            HeroSummary.ToolSlice(SDMTool.Type.CORPSEFINDER, 129L * 1024, 12),
            HeroSummary.ToolSlice(SDMTool.Type.SYSTEMCLEANER, 852L * 1024 * 1024, 51),
            HeroSummary.ToolSlice(SDMTool.Type.APPCLEANER, 512L * 1024 * 1024, 87),
        )
        return HeroSummary(
            mode = HeroSummary.Mode.FREED,
            totalSize = tools.sumOf { it.size },
            itemCount = tools.sumOf { it.count },
            tools = tools,
            timestamp = freedAt,
            residueSize = 5L * 1024 * 1024,
            residueCount = 2,
            lockedTools = listOf(
                HeroSummary.ToolSlice(SDMTool.Type.DEDUPLICATOR, 32L * 1024 * 1024, 60),
            ),
        )
    }

    @Test
    fun `three free chips beside the block still fit at the largest font scale`() {
        // The free chip row is capped at two chips only for FREEABLE summaries; a FREED one can carry
        // three and still show the block, which is more content than the taller height was first
        // rendered against. Same assertions as the sibling above: the block, the leftover line above
        // it and the footer below it must all be on screen, not merely composed.
        val hero = freedWithThreeFreeAndLocked()
        val now = freedAt.plusSeconds(5 * 60)
        hero.showsUpgradeBlock shouldBe true
        renderHero(hero, fontScale = 2f, now = now)

        composeRule.onNodeWithText(blockCaption).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            context.getString(CommonR.string.deduplicator_tool_name),
        ).assertIsDisplayed()
        composeRule.onNodeWithText("left", substring = true).assertIsDisplayed()
        val relativeTime = DateUtils.getRelativeTimeSpanString(
            freedAt.toEpochMilli(),
            now.toEpochMilli(),
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()
        composeRule.onNodeWithText(relativeTime).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w350dp-h900dp")
    fun `the block clears the footer on a narrow display at the largest font scale`() {
        // The header's geometry has to live on the header Row: padding on the dismiss button comes
        // out of the text column's weight(1f) share, and on a narrow display at a large font scale
        // that is enough to wrap the caption onto an extra line and push the block into the footer.
        //
        // Compared by bounds rather than assertIsDisplayed(): that assertion passes on any sliver of
        // visibility, so it would happily accept the block's last row sitting half under the footer.
        val hero = freedWithThreeFreeAndLocked()
        val now = freedAt.plusSeconds(5 * 60)
        renderHero(hero, fontScale = 2f, now = now)

        val relativeTime = DateUtils.getRelativeTimeSpanString(
            freedAt.toEpochMilli(),
            now.toEpochMilli(),
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()
        val chipBottom = composeRule.onNodeWithContentDescription(deduplicatorName)
            .getUnclippedBoundsInRoot().bottom
        val footerTop = composeRule.onNodeWithText(relativeTime).getUnclippedBoundsInRoot().top
        assertTrue(
            "locked chip bottom=$chipBottom must sit strictly above footer top=$footerTop",
            chipBottom < footerTop,
        )
    }

    @Test
    fun `a chip inside the block opens its tool and does not trigger the upgrade`() {
        var upgrades = 0
        var clickedType: SDMTool.Type? = null
        renderHero(
            freedWithLocked(),
            fontScale = 2f,
            onUpgrade = { upgrades++ },
            onLockedToolClick = { clickedType = it },
        )

        composeRule.onNodeWithContentDescription(appCleanerName)
            .assertHasClickAction()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle {
            assertEquals(SDMTool.Type.APPCLEANER, clickedType)
            // The chip consumes its own tap; the block's upgrade action must not fire as well.
            assertEquals(0, upgrades)
        }
    }

    @Test
    fun `tapping the block outside a chip opens the upgrade screen`() {
        var upgrades = 0
        var lockedClicks = 0
        val hero = freedWithLocked()
        renderHero(
            hero,
            fontScale = 2f,
            onUpgrade = { upgrades++ },
            onLockedToolClick = { lockedClicks++ },
        )

        // The caption belongs to the block's own (merging) clickable node, so activating it is the
        // "tapped the block, not a chip" case.
        composeRule.onNodeWithText(blockCaption)
            .assertHasClickAction()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle {
            assertEquals(1, upgrades)
            assertEquals(0, lockedClicks)
        }
    }

    @Test
    fun `swiping the hero down past the threshold dismisses it`() {
        var dismissed = 0
        composeRule.setContent {
            PreviewWrapper {
                BottomBar(
                    state = deleteState(summary()),
                    isVisible = true,
                    heroVisible = true,
                    onMainAction = {},
                    onSettings = {},
                    onUpgrade = {},
                    onDismissHero = { dismissed++ },
                )
            }
        }
        // Drag the hero straight down, well past the dismiss threshold (~35% of card height).
        composeRule.onNodeWithText("can be removed", substring = true).performTouchInput {
            down(center)
            moveBy(Offset(0f, 1000f))
            up()
        }
        composeRule.runOnIdle { assertEquals(1, dismissed) }
    }
}
