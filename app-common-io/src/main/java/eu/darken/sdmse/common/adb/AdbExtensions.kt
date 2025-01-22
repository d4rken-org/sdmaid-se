package eu.darken.sdmse.common.adb

import kotlinx.coroutines.flow.first

suspend fun AdbManager.canUseAdbNow(): Boolean = useAdb.first()