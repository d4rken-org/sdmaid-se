package eu.darken.sdmse.swiper.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.SwapHoriz
import androidx.compose.material.icons.twotone.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.settings.SettingsSwitchItem
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.swiper.R

@Composable
fun SwiperSettingsScreenHost(
    vm: SwiperSettingsViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)
    val state by vm.state.collectAsStateWithLifecycle()

    SwiperSettingsScreen(
        state = state,
        onNavigateUp = vm::navUp,
        onSwapDirectionsChanged = vm::setSwapSwipeDirections,
        onShowDetailsChanged = vm::setShowFileDetailsOverlay,
        onHapticFeedbackChanged = vm::setHapticFeedbackEnabled,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwiperSettingsScreen(
    state: SwiperSettingsViewModel.State = SwiperSettingsViewModel.State(),
    onNavigateUp: () -> Unit = {},
    onSwapDirectionsChanged: (Boolean) -> Unit = {},
    onShowDetailsChanged: (Boolean) -> Unit = {},
    onHapticFeedbackChanged: (Boolean) -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.swiper_label)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = paddingValues,
        ) {
            item {
                SettingsSwitchItem(
                    icon = Icons.TwoTone.SwapHoriz,
                    title = stringResource(R.string.swiper_settings_swap_directions_title),
                    subtitle = stringResource(R.string.swiper_settings_swap_directions_summary),
                    checked = state.swapSwipeDirections,
                    onCheckedChange = onSwapDirectionsChanged,
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.TwoTone.Info,
                    title = stringResource(R.string.swiper_settings_show_details_title),
                    subtitle = stringResource(R.string.swiper_settings_show_details_summary),
                    checked = state.showFileDetailsOverlay,
                    onCheckedChange = onShowDetailsChanged,
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.TwoTone.Vibration,
                    title = stringResource(R.string.swiper_settings_haptic_feedback_title),
                    subtitle = stringResource(R.string.swiper_settings_haptic_feedback_summary),
                    checked = state.hapticFeedbackEnabled,
                    onCheckedChange = onHapticFeedbackChanged,
                )
            }
        }
    }
}

@Preview2
@Composable
private fun SwiperSettingsScreenPreview() {
    PreviewWrapper {
        SwiperSettingsScreen(
            state = SwiperSettingsViewModel.State(
                swapSwipeDirections = false,
                showFileDetailsOverlay = true,
                hapticFeedbackEnabled = true,
            ),
        )
    }
}
