package eu.darken.sdmse.common.upgrade.core.billing

import eu.darken.sdmse.common.upgrade.core.billing.client.BillingConnection
import eu.darken.sdmse.common.upgrade.core.billing.client.BillingConnectionProvider
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.take

internal suspend fun <T> BillingConnectionProvider.use(action: suspend (BillingConnection) -> T): T = connection
    .map { action(it) }
    .take(1)
    .single()