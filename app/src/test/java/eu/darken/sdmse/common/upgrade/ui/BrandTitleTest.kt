package eu.darken.sdmse.common.upgrade.ui

import android.content.Context
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.R
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest
import eu.darken.sdmse.common.R as CommonR

/**
 * Resolves the real flavor resources rather than a sample pattern, so this also proves the two
 * markers survive Android's format path and never reach the user.
 *
 * Flavor-agnostic on purpose: it asserts against whatever this variant's qualifier resource says
 * ("Pro" on GPLAY, "FOSS" on FOSS) so the one test guards both. The resources are flavor-owned, so
 * a variant that compiles proves nothing about the other.
 */
class BrandTitleTest : BaseComposeRobolectricTest() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val name: String
        get() = context.getString(CommonR.string.app_name)

    private val qualifier: String
        get() = context.getString(R.string.app_name_upgrade_postfix)

    private val composed: String
        get() = context.getString(CommonR.string.app_name_upgraded_template, name, qualifier)

    private fun capture(block: @androidx.compose.runtime.Composable () -> AnnotatedString): AnnotatedString {
        lateinit var captured: AnnotatedString
        composeRule.setContent {
            PreviewWrapper { captured = block() }
        }
        composeRule.waitForIdle()
        return captured
    }

    @Test
    fun `without the qualifier the title is the bare app name`() {
        val result = capture { brandTitle(includeQualifier = false, highlightQualifier = false) }

        result.text shouldBe name
        result.spanStyles.size shouldBe 0
    }

    // The regression guard for the two-flag split: this is the FOSS status-free view, which needs
    // the qualifier present but NOT colored. Collapsing the flags drops it; highlighting on
    // `includeQualifier` alone colors it. Both would still produce plausible-looking text, so the
    // span count is the assertion that matters.
    @Test
    fun `an included but unhighlighted qualifier is present and carries no span`() {
        val result = capture { brandTitle(includeQualifier = true, highlightQualifier = false) }

        result.text shouldBe composed
        result.text.contains(qualifier) shouldBe true
        result.spanStyles.size shouldBe 0
    }

    @Test
    fun `a highlighted qualifier carries exactly one span covering the qualifier only`() {
        val result = capture { brandTitle(includeQualifier = true, highlightQualifier = true) }

        result.text shouldBe composed
        result.spanStyles.size shouldBe 1
        val span = result.spanStyles.single()
        // Not just "a span exists" — the bug class this replaces put the highlight on the app name
        // while rendering perfectly correct text.
        result.text.substring(span.start, span.end) shouldBe qualifier
    }

    // The markers are injected as format arguments, so a template or formatter that mangled them
    // would leak U+FFFC / U+FFF9 into the toolbar.
    @Test
    fun `neither splice marker survives into the rendered title`() {
        val result = capture { brandTitle(includeQualifier = true, highlightQualifier = true) }

        result.text shouldNotContain BRAND_TITLE_MARKER
        result.text shouldNotContain BRAND_QUALIFIER_MARKER
    }

    @Test
    fun `the string form matches the annotated form`() {
        val result = capture { AnnotatedString(brandTitleText(includeQualifier = true)) }

        result.text shouldBe composed
    }
}
