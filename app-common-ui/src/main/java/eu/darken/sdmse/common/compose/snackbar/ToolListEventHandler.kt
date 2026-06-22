package eu.darken.sdmse.common.compose.snackbar

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import eu.darken.sdmse.common.R as CommonR
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Drop-in replacement for the boilerplate `LaunchedEffect(vm) { vm.events.collect { … } }`
 * that every tool-list screen used to repeat.
 *
 * Events that implement a [ToolListEvent] sub-interface are translated into snackbars here:
 *  - [ToolListEvent.ShowTaskResult] → snackbar with the result's `primaryInfo`.
 *  - [ToolListEvent.ShowExclusionsCreated] → snackbar with the localized count and a
 *    "View" action that invokes [onShowExclusions] when tapped.
 *
 * Tool-specific events (deletion confirmations, share intents, …) flow through
 * [onOtherEvent] so the caller can keep its own non-shared logic. The parameter is
 * required — passing `{ }` is fine, but doing so consciously prevents accidentally
 * dropping a brand-new event subtype.
 *
 * @param taskResultDuration Duration for the task-result snackbar.
 *   Defaults to [SnackbarDuration.Long]; pass [SnackbarDuration.Short] for tools that
 *   want a less intrusive confirmation.
 */
@Composable
fun <E : Any> ToolListEventHandler(
    events: Flow<E>,
    snackbarHostState: SnackbarHostState,
    onShowExclusions: () -> Unit,
    taskResultDuration: SnackbarDuration = SnackbarDuration.Long,
    onOtherEvent: (E) -> Unit,
) {
    val context = LocalContext.current
    val snackScope = rememberCoroutineScope()
    // The LaunchedEffect below is keyed on `events`, which is stable for the lifetime of
    // the screen. The callbacks and duration, however, are re-created on every
    // recomposition; capture them via rememberUpdatedState so the long-lived collector
    // always sees the latest values.
    val currentTaskResultDuration by rememberUpdatedState(taskResultDuration)
    val currentOnShowExclusions by rememberUpdatedState(onShowExclusions)
    val currentOnOtherEvent by rememberUpdatedState(onOtherEvent)

    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is ToolListEvent.ShowTaskResult -> snackScope.launch {
                    snackbarHostState.showSnackbar(
                        message = event.result.primaryInfo.get(context),
                        duration = currentTaskResultDuration,
                    )
                }

                is ToolListEvent.ShowExclusionsCreated -> snackScope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = context.resources.getQuantityString(
                            CommonR.plurals.exclusion_x_new_exclusions,
                            event.count,
                            event.count,
                        ),
                        actionLabel = context.getString(CommonR.string.general_view_action),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) currentOnShowExclusions()
                }

                else -> currentOnOtherEvent(event)
            }
        }
    }
}
