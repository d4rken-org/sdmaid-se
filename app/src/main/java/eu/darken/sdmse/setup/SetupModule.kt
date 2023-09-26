package eu.darken.sdmse.setup

import androidx.annotation.StringRes
import eu.darken.sdmse.R
import kotlinx.coroutines.flow.Flow

interface SetupModule {
    val state: Flow<State?>

    suspend fun refresh()

    interface State {
        val isComplete: Boolean
    }

    enum class Type(@StringRes val labelRes: Int) {
        USAGE_STATS(R.string.setup_usagestats_title),
        AUTOMATION(R.string.setup_acs_card_title),
        SHIZUKU(R.string.setup_shizuku_card_title),
        ROOT(R.string.setup_root_card_title),
    }
}