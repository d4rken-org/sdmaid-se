package eu.darken.sdmse.setup

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
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

    @Serializable
    enum class Type {
        USAGE_STATS,
        AUTOMATION,
        SHIZUKU,
        ROOT,
        NOTIFICATION,
        SAF,
        STORAGE,
        INVENTORY,
    }
}
