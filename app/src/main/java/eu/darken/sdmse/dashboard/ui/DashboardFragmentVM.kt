package eu.darken.sdmse.dashboard.ui

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.uix.ViewModel3
import javax.inject.Inject

@HiltViewModel
class DashboardFragmentVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
) : ViewModel3(dispatcherProvider = dispatcherProvider)