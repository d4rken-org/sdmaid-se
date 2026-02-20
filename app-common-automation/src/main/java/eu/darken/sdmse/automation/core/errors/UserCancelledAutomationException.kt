package eu.darken.sdmse.automation.core.errors

import kotlinx.coroutines.CancellationException

class UserCancelledAutomationException : CancellationException("User has cancelled automation")