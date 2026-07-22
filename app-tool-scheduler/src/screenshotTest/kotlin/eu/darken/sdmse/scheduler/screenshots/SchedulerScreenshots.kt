package eu.darken.sdmse.scheduler.screenshots

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.scheduler.core.Schedule
import eu.darken.sdmse.scheduler.ui.manager.SchedulerManagerScreen
import eu.darken.sdmse.scheduler.ui.manager.SchedulerManagerViewModel
import kotlinx.coroutines.flow.MutableStateFlow

// Play Store screenshot entry point for the Scheduler screen. Rendered host-side (layoutlib) by the
// com.android.compose.screenshot plugin, one PNG per locale via @PlayStoreLocales. See the Loading /
// populated previews in SchedulerManagerScreen.kt for the mock-state shape.

@PreviewTest
@PlayStoreLocales
@Composable
fun SchedulerScreenshot() {
    PreviewWrapper {
        SchedulerManagerScreen(
            stateSource = MutableStateFlow(
                SchedulerManagerViewModel.State(
                    schedules = listOf(
                        Schedule(
                            id = "screenshot-1",
                            label = "System Cleaning Weekly",
                            hour = 22,
                            minute = 0,
                            useSystemCleaner = true,
                        ),
                        Schedule(
                            id = "screenshot-2",
                            label = "Corpse Finding",
                            hour = 10,
                            minute = 0,
                            useCorpseFinder = true,
                        ),
                    ),
                    showAlarmHint = false,
                    showBatteryHint = false,
                    showCommands = false,
                    isLoading = false,
                ),
            ),
        )
    }
}
