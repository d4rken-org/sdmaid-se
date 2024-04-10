package eu.darken.sdmse.appcontrol.core.automation.specs.androidtv

import dagger.Reusable
import eu.darken.sdmse.appcontrol.core.automation.specs.AppControlLabelSource
import eu.darken.sdmse.common.debug.logging.logTag
import javax.inject.Inject

@Reusable
open class AndroidTVLabels @Inject constructor(
) : AppControlLabelSource {

    companion object {
        val TAG: String = logTag("AppControl", "Automation", "AndroidTV", "Specs")
    }
}
