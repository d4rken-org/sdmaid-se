package eu.darken.sdmse.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.upgrade.ui.FossUpgradeView
import eu.darken.sdmse.common.upgrade.ui.UpgradeScreen
import java.time.Instant

// Renders for Crowdin's translator context, uploaded by the android-translation plugin. Unlike
// PlayStoreLocales these render en-US only: auto-tagging matches the source language, so the other
// locales would be uploads nobody reads. copy_screenshots.sh ignores these names.
@Preview(locale = "en", name = "en-US", device = DS)
private annotation class CrowdinLocale

@PreviewTest
@CrowdinLocale
@Composable
fun FossUpgradePitch() {
    PreviewWrapper {
        UpgradeScreen(view = FossUpgradeView.PITCH)
    }
}

@PreviewTest
@CrowdinLocale
@Composable
fun FossUpgradeStatusFree() {
    PreviewWrapper {
        UpgradeScreen(view = FossUpgradeView.STATUS_FREE)
    }
}

@PreviewTest
@CrowdinLocale
@Composable
fun FossUpgradeStatusUpgraded() {
    PreviewWrapper {
        UpgradeScreen(
            view = FossUpgradeView.STATUS_UPGRADED,
            supporterSince = Instant.parse("2025-03-12T10:15:00Z"),
        )
    }
}
