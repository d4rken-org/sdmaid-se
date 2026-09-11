package eu.darken.sdmse.setup.shizuku

import eu.darken.sdmse.R
import javax.inject.Inject

class FossAdbManagerInstallGuide @Inject constructor() : AdbManagerInstallGuide {
    override val labelRes: Int = R.string.setup_shizuku_install_manager_label
    override val url: String = PORTER_SETUP_URL
    override val porterHelpUrl: String = PORTER_SETUP_URL

    companion object {
        private const val PORTER_SETUP_URL = "https://porter.darken.eu/setup"
    }
}
