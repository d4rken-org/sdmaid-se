package eu.darken.sdmse.common.compose.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * Multi-selection state that survives configuration changes and process death.
 *
 * Drop-in replacement for `remember { mutableStateOf(emptySet<T>()) }` as used by the tool
 * list/detail selection bars. Backed by [rememberSaveable] with a [listSaver]; every SD Maid
 * selection id type is already Bundle-saveable (most are `@Parcelize`, the rest are `String`/`Long`
 * typealiases), so the default Bundle plumbing round-trips them without a bespoke serializer.
 *
 * The `stateSaver` overload is used so the saved/restored value is the [Set] itself, not the
 * wrapping [MutableState].
 */
@Composable
fun <T : Any> rememberSelection(): MutableState<Set<T>> = rememberSaveable(
    stateSaver = listSaver<Set<T>, T>(
        save = { it.toList() },
        restore = { it.toSet() },
    ),
) { mutableStateOf(emptySet()) }
