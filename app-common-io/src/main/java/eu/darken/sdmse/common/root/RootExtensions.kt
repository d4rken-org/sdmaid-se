package eu.darken.sdmse.common.root

import kotlinx.coroutines.flow.first

suspend fun RootManager.useRootNow(): Boolean = useRoot.first()