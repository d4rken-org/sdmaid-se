package eu.darken.sdmse.common.error

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import eu.darken.sdmse.common.navigation.LocalNavigationController
import eu.darken.sdmse.common.uix.ViewModel4
import kotlinx.coroutines.flow.Flow

/**
 * Collects error events from a [ViewModel4] and shows a [ComposeErrorDialog].
 */
@Composable
fun ErrorEventHandler(vm: ViewModel4) {
    ErrorEventHandler(vm.errorEvents)
}

/**
 * Collects error events from any [Flow] of [Throwable] and shows a [ComposeErrorDialog].
 */
@Composable
fun ErrorEventHandler(errorFlow: Flow<Throwable>) {
    val navController = LocalNavigationController.current
    var currentError by remember { mutableStateOf<Throwable?>(null) }

    LaunchedEffect(errorFlow) { errorFlow.collect { error -> currentError = error } }

    currentError?.let { error ->
        ComposeErrorDialog(
            throwable = error,
            onDismiss = { currentError = null },
            navController = navController,
        )
    }
}
