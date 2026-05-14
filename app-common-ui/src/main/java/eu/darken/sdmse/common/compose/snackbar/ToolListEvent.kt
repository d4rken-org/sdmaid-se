package eu.darken.sdmse.common.compose.snackbar

import eu.darken.sdmse.main.core.SDMTool

/**
 * Marker interfaces for events that tool-list screens emit and that
 * [ToolListEventHandler] knows how to render as snackbars.
 *
 * A VM's `Event` sealed type can opt-in by also implementing the matching
 * sub-interface; tool-specific events fall through to the host's own handler.
 */
sealed interface ToolListEvent {

    interface ShowTaskResult : ToolListEvent {
        val result: SDMTool.Task.Result
    }

    interface ShowExclusionsCreated : ToolListEvent {
        val count: Int
    }
}
