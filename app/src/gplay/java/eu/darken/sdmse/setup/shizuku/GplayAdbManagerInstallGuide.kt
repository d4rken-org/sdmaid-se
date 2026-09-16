package eu.darken.sdmse.setup.shizuku

import eu.darken.sdmse.R
import javax.inject.Inject

class GplayAdbManagerInstallGuide @Inject constructor() : AdbManagerInstallGuide {
    override val labelRes: Int = R.string.setup_shizuku_install_manager_label
    override val url: String = "https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api"
    override val porterHelpUrl: String = "https://github.com/d4rken-org/sdmaid-se/wiki/Setup#shizuku"
}
