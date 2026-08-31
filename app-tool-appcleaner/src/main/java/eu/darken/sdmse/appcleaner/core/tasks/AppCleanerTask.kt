package eu.darken.sdmse.appcleaner.core.tasks

import eu.darken.sdmse.main.core.SDMTool

sealed interface AppCleanerTask : SDMTool.Task {
    override val type: SDMTool.Type get() = SDMTool.Type.APPCLEANER

    sealed interface Result : SDMTool.Task.Result {
        override val type: SDMTool.Type get() = SDMTool.Type.APPCLEANER
    }

    /** Why a clean stopped before it was through everything it had targeted. */
    enum class StopReason {
        SCREEN_UNAVAILABLE,
        ERROR,

        /** SD Maid may not use the accessibility service, so the caches only it can reach were left alone. */
        AUTOMATION_NO_CONSENT,
    }
}
