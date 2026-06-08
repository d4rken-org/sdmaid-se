package eu.darken.sdmse.common.debug.logviewer.ui

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.R
import eu.darken.sdmse.common.error.ErrorEventHandler

/**
 * Activity-scoped host for the floating log panel.
 *
 * Placed as a sibling of `NavDisplay` in `MainActivity` (not a nav destination), so `hiltViewModel()`
 * resolves to the Activity's `ViewModelStoreOwner` and the panel survives screen navigation. The
 * lifecycle effects always run (even while not rendered) so capture can be gated on foreground state.
 */
@Composable
fun FloatingLogPanelHost(
    modifier: Modifier = Modifier,
    vm: FloatingLogPanelViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    val context = LocalContext.current

    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.setLifecycleStarted(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.setLifecycleStarted(false) }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is FloatingLogPanelViewModel.Event.LaunchShare -> try {
                    context.startActivity(event.intent)
                } catch (e: ActivityNotFoundException) {
                    vm.onShareLaunchFailed(e)
                }

                FloatingLogPanelViewModel.Event.Copied ->
                    Toast.makeText(context, R.string.debug_logview_copied_msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val rendered by vm.isRendered.collectAsStateWithLifecycle()
    if (!rendered) return

    FloatingLogPanel(
        modifier = modifier,
        stateSource = vm.state,
        onSetQuery = vm::setQuery,
        onNextMatch = vm::nextMatch,
        onPrevMatch = vm::prevMatch,
        onTogglePause = vm::togglePause,
        onSetLevel = vm::setMinPriority,
        onClear = vm::clearBuffer,
        onCopy = vm::copyAll,
        onShare = vm::shareAll,
        onClose = vm::close,
    )
}
