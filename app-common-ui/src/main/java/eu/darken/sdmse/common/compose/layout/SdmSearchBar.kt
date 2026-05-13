package eu.darken.sdmse.common.compose.layout

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import kotlinx.coroutines.delay

@Composable
fun SdmSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(CommonR.string.general_search_action),
    debounceMillis: Long = 300L,
    autoFocus: Boolean = true,
) {
    var localQuery by remember { mutableStateOf(query) }
    val currentQuery by rememberUpdatedState(query)
    val currentOnQueryChange by rememberUpdatedState(onQueryChange)
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(query) {
        if (query != localQuery) localQuery = query
    }
    LaunchedEffect(localQuery, debounceMillis) {
        val pendingQuery = localQuery
        if (pendingQuery == currentQuery) return@LaunchedEffect
        delay(debounceMillis)
        // Re-check that the pending value is still the one we want to emit: a new keystroke would
        // have re-keyed this effect, so seeing pendingQuery == localQuery means no newer text was
        // typed. Without this check, a stale closure could emit ahead of the next debounce window.
        if (pendingQuery == localQuery && pendingQuery != currentQuery) {
            currentOnQueryChange(pendingQuery)
        }
    }
    if (autoFocus) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }

    OutlinedTextField(
        value = localQuery,
        onValueChange = { localQuery = it },
        modifier = modifier.then(if (autoFocus) Modifier.focusRequester(focusRequester) else Modifier),
        placeholder = { Text(placeholder) },
        singleLine = true,
        trailingIcon = {
            IconButton(onClick = {
                if (localQuery.isEmpty()) {
                    onClose()
                } else {
                    localQuery = ""
                    onQueryChange("")
                }
            }) {
                Icon(
                    imageVector = Icons.TwoTone.Close,
                    contentDescription = stringResource(CommonR.string.general_close_action),
                )
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            if (localQuery != currentQuery) onQueryChange(localQuery)
        }),
    )
}

@Preview2
@Composable
private fun SdmSearchBarEmptyPreview() {
    PreviewWrapper {
        SdmSearchBar(
            query = "",
            onQueryChange = {},
            onClose = {},
            modifier = Modifier.fillMaxWidth(),
            autoFocus = false,
        )
    }
}

@Preview2
@Composable
private fun SdmSearchBarPopulatedPreview() {
    PreviewWrapper {
        SdmSearchBar(
            query = "WhatsApp",
            onQueryChange = {},
            onClose = {},
            modifier = Modifier.fillMaxWidth(),
            autoFocus = false,
        )
    }
}
