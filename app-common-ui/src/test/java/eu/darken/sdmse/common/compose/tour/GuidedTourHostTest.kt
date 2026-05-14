package eu.darken.sdmse.common.compose.tour

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class GuidedTourHostTest : BaseComposeRobolectricTest() {

    private val protectedDef = TourDefinition(
        id = TourId("test.protected"),
        steps = listOf(
            TourStep(stepId = "first", body = "Body of step 1".toCaString()),
            TourStep(stepId = "second", body = "Body of step 2".toCaString()),
        ),
        clickProtection = true,
    )

    private val unprotectedDef = protectedDef.copy(
        id = TourId("test.unprotected"),
        clickProtection = false,
    )

    private val centerlessDef = TourDefinition(
        id = TourId("test.centerless"),
        steps = listOf(
            TourStep(
                stepId = "overview",
                targetId = null,
                body = "Body of overview".toCaString(),
            ),
            TourStep(stepId = "second", body = "Body of step 2".toCaString()),
        ),
        clickProtection = true,
    )

    private val targetRect = Rect(left = 100f, top = 100f, right = 200f, bottom = 200f)

    @Test
    fun `idle host renders content`() {
        val sessionFlow = MutableStateFlow<TourSession?>(null)
        composeRule.setHostContent(sessionFlow) {
            Text("CONTENT_MARKER")
        }
        composeRule.onNodeWithText("CONTENT_MARKER").assertExists()
        composeRule.onAllNodesWithText("Body of step 1").assertCountEquals(0)
    }

    @Test
    fun `active session with missing target still renders content`() {
        val sessionFlow = MutableStateFlow<TourSession?>(TourSession(protectedDef, 0))
        composeRule.setHostContent(sessionFlow) {
            Text("CONTENT_MARKER")
        }
        composeRule.onNodeWithText("CONTENT_MARKER").assertExists()
    }

    @Test
    fun `bubble and Next button render when session active and target known`() {
        val sessionFlow = MutableStateFlow<TourSession?>(TourSession(protectedDef, 0))
        composeRule.setHostContent(sessionFlow, preregister = mapOf("first" to targetRect)) {
            Text("UNDER_SCRIM")
        }
        composeRule.onNodeWithText("Body of step 1").assertExists()
        composeRule.onNodeWithContentDescription("Next").assertExists()
    }

    @Test
    fun `Next click invokes onNext`() {
        val sessionFlow = MutableStateFlow<TourSession?>(TourSession(protectedDef, 0))
        var nextCount = 0
        composeRule.setHostContent(
            sessionFlow,
            preregister = mapOf("first" to targetRect),
            onNext = { nextCount++ },
        ) {
            Text("CONTENT_MARKER")
        }
        composeRule.onNodeWithContentDescription("Next").performClick()
        nextCount shouldBeEq 1
    }

    @Test
    fun `Skip icon opens confirm — neither callback fires yet`() {
        val sessionFlow = MutableStateFlow<TourSession?>(TourSession(protectedDef, 0))
        var skipCount = 0
        var dismissCount = 0
        composeRule.setHostContent(
            sessionFlow,
            preregister = mapOf("first" to targetRect),
            onSkipForNow = { skipCount++ },
            onDontShowAgain = { dismissCount++ },
        ) {
            Text("CONTENT_MARKER")
        }
        // Pre-state: confirm not visible.
        composeRule.onAllNodesWithText("Skip the tour?").assertCountEquals(0)
        // Tap the X — confirm appears, no controller calls yet.
        composeRule.onNodeWithContentDescription("Skip").performClick()
        composeRule.onNodeWithText("Skip the tour?").assertExists()
        skipCount shouldBeEq 0
        dismissCount shouldBeEq 0
    }

    @Test
    fun `confirm Continue tour returns to step view without firing callbacks`() {
        val sessionFlow = MutableStateFlow<TourSession?>(TourSession(protectedDef, 0))
        var skipCount = 0
        var dismissCount = 0
        composeRule.setHostContent(
            sessionFlow,
            preregister = mapOf("first" to targetRect),
            onSkipForNow = { skipCount++ },
            onDontShowAgain = { dismissCount++ },
        ) {
            Text("CONTENT_MARKER")
        }
        composeRule.onNodeWithContentDescription("Skip").performClick()
        composeRule.onNodeWithText("Continue tour").performClick()
        composeRule.onNodeWithText("Body of step 1").assertExists()
        skipCount shouldBeEq 0
        dismissCount shouldBeEq 0
    }

    @Test
    fun `confirm Skip for now invokes onSkipForNow only`() {
        val sessionFlow = MutableStateFlow<TourSession?>(TourSession(protectedDef, 0))
        var skipCount = 0
        var dismissCount = 0
        composeRule.setHostContent(
            sessionFlow,
            preregister = mapOf("first" to targetRect),
            onSkipForNow = { skipCount++ },
            onDontShowAgain = { dismissCount++ },
        ) {
            Text("CONTENT_MARKER")
        }
        composeRule.onNodeWithContentDescription("Skip").performClick()
        composeRule.onNodeWithText("Skip for now").performClick()
        skipCount shouldBeEq 1
        dismissCount shouldBeEq 0
    }

    @Test
    fun `confirm Don't show again invokes onDontShowAgain only`() {
        val sessionFlow = MutableStateFlow<TourSession?>(TourSession(protectedDef, 0))
        var skipCount = 0
        var dismissCount = 0
        composeRule.setHostContent(
            sessionFlow,
            preregister = mapOf("first" to targetRect),
            onSkipForNow = { skipCount++ },
            onDontShowAgain = { dismissCount++ },
        ) {
            Text("CONTENT_MARKER")
        }
        composeRule.onNodeWithContentDescription("Skip").performClick()
        composeRule.onNodeWithText("Don't show again").performClick()
        skipCount shouldBeEq 0
        dismissCount shouldBeEq 1
    }

    @Test
    fun `clickProtection true blocks underlying clickable but Next still works`() {
        val sessionFlow = MutableStateFlow<TourSession?>(TourSession(protectedDef, 0))
        var underlyingClicks = 0
        var nextCount = 0
        composeRule.setHostContent(
            sessionFlow,
            preregister = mapOf("first" to targetRect),
            onNext = { nextCount++ },
        ) {
            // Small clickable at top-start. The bubble (placeBelow = true given the target rect)
            // is anchored top-center, padded down 16dp from targetRect.bottom (~216dp) — its
            // y-range never reaches 0..40, so UNDER's tap center sits outside the bubble.
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.TopStart)
                        .testTag("UNDER")
                        .clickable { underlyingClicks++ },
                )
            }
        }
        composeRule.onNodeWithTag("UNDER").performClick()
        underlyingClicks shouldBeEq 0
        composeRule.onNodeWithContentDescription("Next").performClick()
        nextCount shouldBeEq 1
    }

    @Test
    fun `clickProtection false lets clicks reach underlying content via overlay-visible regions`() {
        val sessionFlow = MutableStateFlow<TourSession?>(TourSession(unprotectedDef, 0))
        var underlyingClicks = 0
        composeRule.setHostContent(
            sessionFlow,
            preregister = mapOf("first" to targetRect),
        ) {
            // Top-start clickable, see geometry note in the protected variant above.
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.TopStart)
                        .testTag("UNDER")
                        .clickable { underlyingClicks++ },
                )
            }
        }
        composeRule.onNodeWithTag("UNDER").performClick()
        underlyingClicks shouldBeGt 0
    }

    @Test
    fun `centerless step renders body without any registered target`() {
        val sessionFlow = MutableStateFlow<TourSession?>(TourSession(centerlessDef, 0))
        composeRule.setHostContent(sessionFlow) {
            Text("CONTENT_MARKER")
        }
        composeRule.onNodeWithText("Body of overview").assertExists()
        composeRule.onNodeWithContentDescription("Next").assertExists()
    }

    @Test
    fun `centerless step does not auto-skip past the grace window`() {
        val sessionFlow = MutableStateFlow<TourSession?>(TourSession(centerlessDef, 0))
        var nextCount = 0
        composeRule.mainClock.autoAdvance = false
        composeRule.setHostContent(sessionFlow, onNext = { nextCount++ }) {
            Text("CONTENT_MARKER")
        }
        // Advance well past MISSING_TARGET_GRACE_MS — if grace-skip leaked, onNext would fire.
        composeRule.mainClock.advanceTimeBy(MISSING_TARGET_GRACE_MS * 4)
        composeRule.mainClock.autoAdvance = true
        nextCount shouldBeEq 0
        composeRule.onNodeWithText("Body of overview").assertExists()
    }

    @Test
    fun `clickProtection blocks underlying clicks during centerless step`() {
        val sessionFlow = MutableStateFlow<TourSession?>(TourSession(centerlessDef, 0))
        var underlyingClicks = 0
        var nextCount = 0
        composeRule.setHostContent(
            sessionFlow,
            onNext = { nextCount++ },
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.TopStart)
                        .testTag("UNDER")
                        .clickable { underlyingClicks++ },
                )
            }
        }
        composeRule.onNodeWithTag("UNDER").performClick()
        underlyingClicks shouldBeEq 0
        composeRule.onNodeWithContentDescription("Next").performClick()
        nextCount shouldBeEq 1
    }

    // BackHandler priority is verified by manual QA on the FOSS debug build:
    //   - Pressing back during an active tour invokes the host's onSkipForNow.
    //   - When no session is active, back falls through to NavDisplay/screen BackHandlers.
    // We don't test it here because the Compose-activity BackHandler hooks deep into the host
    // activity's OnBackPressedDispatcher in ways that don't reliably stand up under Robolectric
    // + a custom LocalOnBackPressedDispatcherOwner without instrumentation. The host code uses
    // the standard composition idiom (BackHandler is composed AFTER content() in the same Box,
    // so it registers its callback later and wins LIFO dispatch).
}

private fun ComposeContentTestRule.setHostContent(
    session: StateFlow<TourSession?>,
    preregister: Map<String, Rect> = emptyMap(),
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onSkipForNow: () -> Unit = {},
    onDontShowAgain: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    // Pre-seed a registry the host will use directly (skips real layout).
    val registry = TourTargetRegistry()
    preregister.forEach { (id, rect) -> registry.put(id, rect, owner = id) }
    setContent {
        PreviewWrapper {
            GuidedTourHost(
                session = session,
                onNext = onNext,
                onPrevious = onPrevious,
                onSkipForNow = onSkipForNow,
                onDontShowAgain = onDontShowAgain,
                modifier = Modifier.fillMaxSize(),
                registry = registry,
                content = content,
            )
        }
    }
}

private infix fun Int.shouldBeEq(other: Int) {
    if (this != other) error("expected $other, got $this")
}

private infix fun Int.shouldBeGt(other: Int) {
    if (this <= other) error("expected > $other, got $this")
}
