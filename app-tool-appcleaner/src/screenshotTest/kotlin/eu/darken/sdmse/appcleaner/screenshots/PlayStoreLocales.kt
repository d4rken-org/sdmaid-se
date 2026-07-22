package eu.darken.sdmse.appcleaner.screenshots

import androidx.compose.ui.tooling.preview.Preview

// Keep this file identical across all screenshot modules (per-module copies are required because a
// screenshotTest source set can't see another module's). The locale set is also documented in
// fastlane/screenshots/locales.txt; there is no auto-generation yet, so edit both by hand when adding
// locales. `name` is the fastlane metadata directory (used by copy_screenshots.sh); `locale` is the
// Android resource qualifier. DS matches the current Play Store screenshots (1080x2400).

internal const val DS = "spec:width=1080px,height=2400px,dpi=428"

/** Committed "smoke" locale set: LTR, RTL (ar), and CJK (ja, zh) coverage. */
@Preview(locale = "en", name = "en-US", device = DS)
@Preview(locale = "de", name = "de-DE", device = DS)
@Preview(locale = "es", name = "es-ES", device = DS)
@Preview(locale = "fr", name = "fr-FR", device = DS)
@Preview(locale = "ja", name = "ja-JP", device = DS)
@Preview(locale = "zh-rCN", name = "zh-CN", device = DS)
@Preview(locale = "ru", name = "ru-RU", device = DS)
@Preview(locale = "ar", name = "ar", device = DS)
annotation class PlayStoreLocales
