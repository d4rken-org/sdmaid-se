package eu.darken.sdmse.setup

import androidx.annotation.StringRes
import eu.darken.sdmse.R
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface SetupModule {
    val state: Flow<State>

    suspend fun refresh()

    sealed interface State {
        val type: Type

        interface Loading : State {
            val startAt: Instant
        }

        interface Current : State {
            val isComplete: Boolean
        }
    }

    enum class Type(@StringRes val labelRes: Int) {
        USAGE_STATS(R.string.setup_usagestats_title),
        AUTOMATION(R.string.setup_acs_card_title),
        SHIZUKU(R.string.setup_shizuku_card_title),
        ROOT(R.string.setup_root_card_title),
        NOTIFICATION(R.string.setup_notification_title),
        SAF(R.string.setup_saf_card_title),
        STORAGE(R.string.setup_manage_storage_card_title),
        INVENTORY(R.string.setup_inventory_card_title),
    }
}