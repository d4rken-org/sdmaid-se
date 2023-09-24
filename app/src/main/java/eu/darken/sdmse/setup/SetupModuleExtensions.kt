package eu.darken.sdmse.setup

import kotlinx.coroutines.flow.first

suspend fun SetupModule.isComplete() = state.first()?.isComplete ?: false