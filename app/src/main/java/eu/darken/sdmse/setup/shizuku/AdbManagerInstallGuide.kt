package eu.darken.sdmse.setup.shizuku

import androidx.annotation.StringRes

/**
 * Where to send a user who has no ADB manager installed, and which one to name.
 *
 * Flavor-bound: the Google Play build may not point at an app install source outside Play, so it
 * names Shizuku and links to its Play listing, while FOSS names Porter and links to porter.darken.eu.
 */
interface AdbManagerInstallGuide {
    /** Brand name of the manager this build can actually deliver. */
    @get:StringRes val labelRes: Int

    /** Opened by the install action. */
    val url: String

    /**
     * Help target for an active Porter backend.
     *
     * Porter's own setup guide is the better page, but it doubles as its install instructions, so
     * the Play build sends users to our wiki instead.
     */
    val porterHelpUrl: String
}
