package eu.darken.sdmse.systemcleaner.core.filter.custom

import kotlinx.coroutines.flow.first


suspend fun CustomFilterRepo.currentConfigs() = configs.first()