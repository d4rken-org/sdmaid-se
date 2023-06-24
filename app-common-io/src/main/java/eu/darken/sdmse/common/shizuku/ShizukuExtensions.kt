package eu.darken.sdmse.common.shizuku

import kotlinx.coroutines.flow.first

suspend fun ShizukuManager.canUseShizukuNow(): Boolean = useShizuku.first()