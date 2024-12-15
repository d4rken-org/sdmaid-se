package eu.darken.sdmse.automation.core.errors

import androidx.annotation.Keep

@Keep
open class UnclickableTargetException(message: String?, cause: Throwable?) : AutomationException(message, cause) {
    constructor(message: String?) : this(message, null)
}