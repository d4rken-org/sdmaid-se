package eu.darken.sdmse.automation.core

import kotlinx.coroutines.flow.first

suspend fun AutomationManager.canUseAcsNow(): Boolean = useAcs.first()